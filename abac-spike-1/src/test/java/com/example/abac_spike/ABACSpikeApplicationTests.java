package com.example.abac_spike;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.AfterEach;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.BeforeEach;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Context;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Describe;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.It;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.isIn;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import javax.imageio.ImageIO;
import javax.persistence.EntityManager;

import be.heydari.AstWalker;
import be.heydari.lib.converters.protobuf.ProtobufUtils;
import be.heydari.lib.converters.protobuf.generated.PDisjunction;
import be.heydari.lib.expressions.Disjunction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.auth.BasicUserPrincipal;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.versions.VersionInfo;

import com.github.paulcwarren.ginkgo4j.Ginkgo4jConfiguration;
import com.github.paulcwarren.ginkgo4j.Ginkgo4jSpringRunner;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.path.json.JsonPath;
import org.springframework.web.client.RestTemplate;

@RunWith(Ginkgo4jSpringRunner.class)
@Ginkgo4jConfiguration(threads = 1)
@SpringBootTest(classes = {
        ABACSpikeApplication.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ABACSpikeApplicationTests {

    @LocalServerPort
    int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private EntityManager em;

    @Autowired
    private BrokerRepository brokerRepo;

    @Autowired
    private AccountStateRepository repo;

    @Autowired
    private AccountStateStore store;

    @Autowired
    private SolrClient solr;

    private AccountState doc;

    private JsonPath json;

    private String tenantFooDoc1;
    private String tenantFooDoc1Content;
    private String tenantFooDoc2;
    private String tenantFooDoc2Content;
    private String tenantFooDoc3;
    private String tenantFooDoc3Content;

    private String tenantBarDoc1;
    private String tenantBarDoc1Content;

    private String brokerFooUri;
    private String brokerBarUri;

    {
        Describe("ABAC Example", () -> {
            BeforeEach(() -> {
                RestAssured.port = port;
            });

            Context("when account statements are added", () -> {

                BeforeEach(() -> {

                    // add some brokers
                    {
                        json = given()
                                .header("content-type", "application/hal+json")
                                .body("{\"name\":\"foo\"}")
                                .post("/brokers/")
                                .then()
                                .statusCode(HttpStatus.SC_CREATED)
                                .extract()
                                .jsonPath();

                        brokerFooUri = (String) json
                                .get("_links.self.href");

                        json = given()
                                .header("content-type", "application/hal+json")
                                .body("{\"name\":\"bar\"}")
                                .post("/brokers/")
                                .then()
                                .statusCode(HttpStatus.SC_CREATED)
                                .extract()
                                .jsonPath();

                        brokerBarUri = (String) json
                                .get("_links.self.href");
                    }

                    // add account statements owned by brokers' broker ID
                    {
                        json = given()
                                /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                        .substringAfter(brokerFooUri, "/brokers/")))*/
                                .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                                .substringAfter(brokerFooUri, "/brokers/"))))
                                .header("content-type", "application/hal+json")
                                .body("{\"name\":\"zzz\",\"type\":\"sop_document\"}")
                                .post("/accountStates/")
                                .then()
                                .statusCode(HttpStatus.SC_CREATED)
                                .extract()
                                .jsonPath();

                        tenantFooDoc1 = (String) json
                                .get("_links.self.href");
                        tenantFooDoc1Content = (String) json
                                .get("_links.accountStates.href");

                        json = given()
                                .contentType("text/uri-list")
                                .body(format("%s", brokerFooUri))
                                .put(tenantFooDoc1
                                        + "/broker")
                                .then()
                                .statusCode(HttpStatus.SC_NO_CONTENT)
                                .extract()
                                .jsonPath();

                        given()
                        .config(RestAssured
                                .config()
                                .encoderConfig(encoderConfig()
                                        .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                        /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                .substringAfter(brokerFooUri, "/brokers/")))*/
                        .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                .substringAfter(brokerFooUri, "/brokers/"))))
                        .header("content-type", "text/plain")
                        .body(IOUtils
                                .toByteArray("foo doc 1"))
                        .post(tenantFooDoc1Content)
                        .then()
                        .statusCode(HttpStatus.SC_CREATED);

                        json = given()
                                /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                        .substringAfter(brokerBarUri, "/brokers/")))*/
                                .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                        .substringAfter(brokerBarUri, "/brokers/"))))
                                .header("content-type", "application/hal+json")
                                .body("{\"name\":\"www\",\"type\":\"sop_document\"}")
                                .post("/accountStates/")
                                .then()
                                .statusCode(HttpStatus.SC_CREATED)
                                .extract()
                                .jsonPath();

                        tenantBarDoc1 = (String) json
                                .get("_links.self.href");
                        tenantBarDoc1Content = (String) json
                                .get("_links.accountStates.href");

                        json = given()
                                .contentType("text/uri-list")
                                .body(format("%s", brokerBarUri))
                                .put(tenantBarDoc1
                                        + "/broker")
                                .then()
                                .statusCode(HttpStatus.SC_NO_CONTENT)
                                .extract()
                                .jsonPath();

                        given()
                        .config(RestAssured
                                .config()
                                .encoderConfig(encoderConfig()
                                        .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                        /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                .substringAfter(brokerBarUri, "/brokers/")))*/
                        .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                .substringAfter(brokerBarUri, "/brokers/"))))
                        .header("content-type", "text/plain")
                        .body(IOUtils
                                .toByteArray("bar doc 1"))
                        .post(tenantBarDoc1Content)
                        .then()
                        .statusCode(HttpStatus.SC_CREATED);

                        json = given()
                                /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                        .substringAfter(brokerFooUri, "/brokers/")))*/
                                .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                        .substringAfter(brokerFooUri, "/brokers/"))))
                                .header("content-type", "application/hal+json")
                                .body("{\"name\":\"ppp\",\"type\":\"sop_document\"}")
                                .post("/accountStates/")
                                .then()
                                .statusCode(HttpStatus.SC_CREATED)
                                .extract()
                                .jsonPath();

                        tenantFooDoc2 = (String) json
                                .get("_links.self.href");
                        tenantFooDoc2Content = (String) json
                                .get("_links.accountStates.href");

                        json = given()
                                .contentType("text/uri-list")
                                .body(format("%s", brokerFooUri))
                                .put(tenantFooDoc2
                                        + "/broker")
                                .then()
                                .statusCode(HttpStatus.SC_NO_CONTENT)
                                .extract()
                                .jsonPath();

                        given()
                        .config(RestAssured
                                .config()
                                .encoderConfig(encoderConfig()
                                        .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                        /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                .substringAfter(brokerFooUri, "/brokers/")))*/
                        .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                .substringAfter(brokerFooUri, "/brokers/"))))
                        .header("content-type", "text/plain")
                        .body(IOUtils
                                .toByteArray("foo doc 2"))
                        .post(tenantFooDoc2Content)
                        .then()
                        .statusCode(HttpStatus.SC_CREATED);

                        json = given()
                                /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                        .substringAfter(brokerFooUri, "/brokers/")))*/
                                .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                        .substringAfter(brokerFooUri, "/brokers/"))))
                                .header("content-type", "application/hal+json")
                                .body("{\"name\":\"aaa\",\"type\":\"sop_document\"}")
                                .post("/accountStates/")
                                .then()
                                .statusCode(HttpStatus.SC_CREATED)
                                .extract()
                                .jsonPath();

                        tenantFooDoc3 = (String) json
                                .get("_links.self.href");
                        tenantFooDoc3Content = (String) json
                                .get("_links.accountStates.href");

                        json = given()
                                .contentType("text/uri-list")
                                .body(format("%s", brokerFooUri))
                                .put(tenantFooDoc3
                                        + "/broker")
                                .then()
                                .statusCode(HttpStatus.SC_NO_CONTENT)
                                .extract()
                                .jsonPath();

                        given()
                        .config(RestAssured
                                .config()
                                .encoderConfig(encoderConfig()
                                        .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                        /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                .substringAfter(brokerFooUri, "/brokers/")))*/
                        .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                .substringAfter(brokerFooUri, "/brokers/"))))
                        .header("content-type", "text/plain")
                        .body(IOUtils
                                .toByteArray("foo doc 3"))
                        .post(tenantFooDoc3Content)
                        .then()
                        .statusCode(HttpStatus.SC_CREATED);
                    }

                    // add some other documents
                    {
                        given()
                        .header("content-type", "application/hal+json")
                        .body("{\"type\":\"other\"}")
                        .post("/otherDocuments/")
                        .then()
                        .statusCode(HttpStatus.SC_CREATED);

                        given()
                        .header("content-type", "application/hal+json")
                        .body("{\"type\":\"other\"}")
                        .post("/otherDocuments/")
                        .then()
                        .statusCode(HttpStatus.SC_CREATED);
                    }
                });

                AfterEach(() -> {
                    if (solr != null) {
                        UpdateRequest req = new UpdateRequest();
                        req.deleteByQuery("*");
                        req.setBasicAuthCredentials("solr", "SolrRocks");
                        req.process(solr, null);
                        req.commit(solr, null);
                    }
                });

                Context("#findAll", () -> {

                    Context("given a findAll for account states executed by broker foo", () -> {

                        BeforeEach(() -> {
                            json = given()
                                    .config(RestAssured
                                            .config()
                                            .encoderConfig(encoderConfig()
                                                    .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                                    /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                            .substringAfter(brokerFooUri, "/brokers/")))*/
                                    .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                            .substringAfter(brokerFooUri, "/brokers/"))))
                                    .get("/accountStates?page=0&size=2&sort=name&name.dir=asc")
                                    .then()
                                    .statusCode(HttpStatus.SC_OK)
                                    .extract()
                                    .jsonPath();

                        });

                        It("should only return account statements owned by broker foo", () -> {
                            int count = json
                                    .getList("_embedded.accountStates")
                                    .size();
                            assertThat(count, is(2));
                            String previousName = "";
                            for (int i = 0; i < count; i++) {
                                // assertThat(json.getString(format("_embedded.accountStates[%s].brokerId",
                                // i)), is("foo"));
                                assertThat(json
                                        .getString(format("_embedded.accountStates[%s].type", i)), is("sop_document"));
                                assertThat(json
                                        .getString(format("_embedded.accountStates[%s].name", i)), is(isIn(new String[] {
                                                "aaa",
                                        "ppp" })));
                                assertThat(json
                                        .getString(format("_embedded.accountStates[%s].name", i)), is(greaterThanOrEqualTo(previousName)));
                                previousName = json
                                        .getString(format("_embedded.accountStates[%s].name", i));
                            }
                        });
                    });
                });

                Context("#findBy methods", () -> {

                    Context("given a custom findByXYZ is executed by broker foo", () -> {

                        BeforeEach(() -> {
                            json = given()
                                    .config(RestAssured
                                            .config()
                                            .encoderConfig(encoderConfig()
                                                    .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                                    /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                            .substringAfter(brokerFooUri, "/brokers/")))*/
                                    .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                            .substringAfter(brokerFooUri, "/brokers/"))))
                                    .header("accept", "application/hal+json")
                                    .get("/accountStates/search/findByType?type=sop_document&page=0&size=2&sort=name&name.dir=asc")
                                    .then()
                                    .statusCode(HttpStatus.SC_OK)
                                    .extract()
                                    .jsonPath();

                        });

                        It("should only return account statements owned by broker foo", () -> {
                            int count = json
                                    .getList("_embedded.accountStates")
                                    .size();
                            assertThat(count, is(2));
                            String previousName = "";
                            for (int i = 0; i < count; i++) {
                                assertThat(json
                                        .getString(format("_embedded.accountStates[%s].type", i)), is("sop_document"));
                                assertThat(json
                                        .getString(format("_embedded.accountStates[%s].name", i)), is(isIn(new String[] {
                                                "aaa",
                                        "ppp" })));
                                assertThat(json
                                        .getString(format("_embedded.accountStates[%s].name", i)), is(greaterThanOrEqualTo(previousName)));
                                previousName = json
                                        .getString(format("_embedded.accountStates[%s].name", i));
                            }
                        });
                    });

                });

                Context("@Query methods", () -> {

                    Context("when a custom query method is executed by broker foo", () -> {

                        BeforeEach(() -> {
                            json = given()
                                    .config(RestAssured
                                            .config()
                                            .encoderConfig(encoderConfig()
                                                    .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                                    /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                            .substringAfter(brokerFooUri, "/brokers/")))*/
                                    .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                            .substringAfter(brokerFooUri, "/brokers/"))))
                                    .header("accept", "application/hal+json")
                                    .get("/accountStates/search/byType?type=sop_document")
                                    .then()
                                    .statusCode(HttpStatus.SC_OK)
                                    .extract()
                                    .jsonPath();
                        });

                        It("should only return account statements owned by broker foo", () -> {
                            int count = json
                                    .getList("_embedded.accountStates")
                                    .size();
                            for (int i = 0; i < count; i++) {
                                assertThat(json
                                        .getString(format("_embedded.accountStates[%s].name", i)), is(isIn(new String[] {
                                                "aaa",
                                                "ppp",
                                        "zzz" })));
                                assertThat(json
                                        .getString(format("_embedded.accountStates[%s].type", i)), is("sop_document"));
                            }
                        });
                    });
                });

                Context("#save", () -> {

                    Context("when a broker updates an account statement they own", () -> {

                        It("should succeed", () -> {

                            int statusCode = given()
                                    .config(RestAssured
                                            .config()
                                            .encoderConfig(encoderConfig()
                                                    .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                                    /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                            .substringAfter(brokerFooUri, "/brokers/")))*/
                                    .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                            .substringAfter(brokerFooUri, "/brokers/"))))
                                    .header("Content-Type", "application/hal+json")
                                    .body("{\"name\":\"zzz updated\"}")
                                    .put(tenantFooDoc1)
                                    .then()
                                    .statusCode(HttpStatus.SC_OK)
                                    .extract()
                                    .statusCode();

                            assertThat(statusCode, is(200));

                            Optional<AccountState> one = repo
                                    .findById(Long
                                            .parseLong(StringUtils
                                                    .substringAfter(tenantFooDoc1, "/accountStates/")));
                            assertThat(one
                                    .get()
                                    .getName(), is("zzz updated"));
                        });
                    });

                    Context("when a broker updates an account statement they do not own", () -> {

                        It("should fail with a 404", () -> {

                            given()
                            .config(RestAssured
                                    .config()
                                    .encoderConfig(encoderConfig()
                                            .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                            /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/")))*/
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/"))))
                            .header("Content-Type", "application/hal+json")
                            .body("{\"name\":\"zzz updated\",\"vstamp\":1}")
                            .put(tenantFooDoc1)
                            .then()
                            .statusCode(HttpStatus.SC_NOT_FOUND);
                        });
                    });
                });

                Context("#delete", () -> {

                    Context("when a broker deletes an account statement they own", () -> {

                        It("should succeed", () -> {

                            given()
                            .config(RestAssured
                                    .config()
                                    .encoderConfig(encoderConfig()
                                            .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                            /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                    .substringAfter(brokerFooUri, "/brokers/")))*/
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerFooUri, "/brokers/"))))
                            .header("Accept", "application/hal+json")
                            .delete(tenantFooDoc1)
                            .then()
                            .statusCode(HttpStatus.SC_NO_CONTENT);

                            Optional<AccountState> one = repo
                                    .findById(Long
                                            .parseLong(StringUtils
                                                    .substringAfter(tenantFooDoc1, "/accountStates/")));
                            assertThat(one
                                    .isPresent(), is(false));
                        });
                    });

                    Context("when a broker deletes an account statement they do not own", () -> {

                        It("should fail with a 404", () -> {

                            given()
                            .config(RestAssured
                                    .config()
                                    .encoderConfig(encoderConfig()
                                            .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                            /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/")))*/
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/"))))
                            .header("Accept", "application/hal+json")
                            .delete(tenantFooDoc1)
                            .then()
                            .statusCode(HttpStatus.SC_NOT_FOUND);
                        });
                    });
                });

                Context("#content", () -> {

                    Context("when a broker gets content they own", () -> {

                        It("should succeed", () -> {

                            String body = given()
                                    .config(RestAssured
                                            .config()
                                            .encoderConfig(encoderConfig()
                                                    .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                                    /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                            .substringAfter(brokerFooUri, "/brokers/")))*/
                                    .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                            .substringAfter(brokerFooUri, "/brokers/"))))
                                    .header("Accept", "text/plain")
                                    .get(tenantFooDoc1Content)
                                    .then()
                                    .statusCode(HttpStatus.SC_OK)
                                    .extract()
                                    .body()
                                    .asString();

                            assertThat(body, is("foo doc 1"));
                        });
                    });

                    Context("when a broker gets content they do not own", () -> {

                        It("should fail with a 404", () -> {

                            given()
                            .config(RestAssured
                                    .config()
                                    .encoderConfig(encoderConfig()
                                            .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                            /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/")))*/
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/"))))
                            .header("Accept", "text/plain")
                            .put(tenantFooDoc1Content)
                            .then()
                            .statusCode(HttpStatus.SC_NOT_FOUND);
                        });
                    });
                });

                Context("#renditions", () -> {

                    Context("when a broker gets a rendition of content they own", () -> {

                        It("should succeed", () -> {

                            byte[] body = given()
                                    .config(RestAssured
                                            .config()
                                            .encoderConfig(encoderConfig()
                                                    .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                                    /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                            .substringAfter(brokerFooUri, "/brokers/")))*/
                                    .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                            .substringAfter(brokerFooUri, "/brokers/"))))
                                    .header("Accept", "image/jpeg")
                                    .get(tenantFooDoc1Content)
                                    .then()
                                    .statusCode(HttpStatus.SC_OK)
                                    .extract()
                                    .body()
                                    .asByteArray();

                            ByteArrayInputStream bis = new ByteArrayInputStream(
                                    body);
                            BufferedImage img = ImageIO
                                    .read(bis);
                            assertThat(img, is(not(nullValue())));
                        });
                    });

                    Context("when a broker gets a rendition for content they do not own", () -> {

                        It("should fail with a 404", () -> {

                            given()
                            .config(RestAssured
                                    .config()
                                    .encoderConfig(encoderConfig()
                                            .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                            /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/")))*/
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/"))))
                            .header("Accept", "image/jpeg")
                            .get(tenantFooDoc1Content)
                            .then()
                            .statusCode(HttpStatus.SC_NOT_FOUND);
                        });
                    });
                });

                Context("#versions", () -> {

                    BeforeEach(() -> {
                        SecurityContextHolder
                        .getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(
                                new BasicUserPrincipal(
                                        "tuser"),
                                null,
                                null));

                        doc = repo
                                .findById(Long
                                        .parseLong(StringUtils
                                                .substringAfter(tenantFooDoc1, "/accountStates/")))
                                .get();
                        doc = repo
                                .lock(doc);
                        doc = repo
                                .version(doc, new VersionInfo(
                                        "1.1",
                                        "Minor version"));
                        doc = repo
                                .unlock(doc);
                        doc = repo
                                .save(doc);
                    });

                    Context("when a broker gets a version of an account state they own", () -> {

                        It("should succeed", () -> {

                            given()
                            .config(RestAssured
                                    .config()
                                    .encoderConfig(encoderConfig()
                                            .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                            /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                    .substringAfter(brokerFooUri, "/brokers/")))*/
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerFooUri, "/brokers/"))))
                            .header("Accept", "application/hal+json")
                            .get("/accountStates/"
                                    + doc
                                    .getId())
                            .then()
                            .statusCode(HttpStatus.SC_OK);
                        });
                    });

                    Context("when a broker gets a rendition for content they do not own", () -> {

                        It("should fail with a 404", () -> {

                            given()
                            .config(RestAssured
                                    .config()
                                    .encoderConfig(encoderConfig()
                                            .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                            /*.header("X-ABAC-Context", format("broker.id = %sL", StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/")))*/
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/"))))
                            .header("Accept", "application/hal+json")
                            .get("/accountStates/"
                                    + doc
                                    .getId())
                            .then().statusCode(HttpStatus.SC_NOT_FOUND);
                        });
                    });
                });

                Context("#fulltext", () -> {

                    Context("when a broker performs a search", () -> {

                        It("should only return documents they own", () -> {

                            JsonPath results = given()
                                    .config(RestAssured.config()
                                            .encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
/*
                                    .header("X-ABAC-Context", format("broker.id = %sL", StringUtils.substringAfter(brokerFooUri, "/brokers/")))
*/
                                    .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils.substringAfter(brokerFooUri, "/brokers/"))))
                                    .header("Accept", "application/hal+json")
                                    .get(searchContentEndpoint(tenantFooDoc1, "doc"))
                                    .then()
                                    .statusCode(HttpStatus.SC_OK)
                                    .extract().jsonPath();

                            List<String> links = results.get("_embedded.accountStates._links.self.href");
                            assertThat(links.size(), is(greaterThan(0)));
                            for (String link : links) {
                                Optional<AccountState> accountState = repo.findById(Long.parseLong(StringUtils.substringAfterLast(link, "/")));
                                assertThat(accountState.isPresent(), is(true));
                                assertThat(accountState.get().getBroker().getId(), is(Long.parseLong(StringUtils.substringAfter(brokerFooUri, "/brokers/"))));
                            }
                        });
                    });
                });
            });
        });
    }

    @Test
    public void noop() {
    }

    private static String searchContentEndpoint(String entityEndpoint, String queryString) {
        return StringUtils.substringBeforeLast(entityEndpoint, "/") + "/searchContent?queryString=" + queryString;
    }


    public static String queryOPA(Long brokerId) throws IOException {
        OpaQuery opaQuery = OpaQuery.builder()
                .query("data.abac_spike.allow_partial == false")
                .input(new OpaInput("GET", format("%sL", brokerId)))
                .unknowns(Collections.singletonList("data.accountState"))
                .build();

        String residualPolicy = new RestTemplate()
                .postForObject("http://127.0.0.1:8181/v1/compile", opaQuery, String.class);

        //ResponseAST
        Disjunction disjunction = AstWalker.walk(residualPolicy);
        PDisjunction pDisjunction = ProtobufUtils.from(disjunction, "");
        byte[] protoBytes = pDisjunction.toByteArray();
        return Base64.getEncoder().encodeToString(protoBytes);
    }


    /**
     * Example:
     * {
     *   "query": "data.abac_spike.allow_partial == false",
     *   "input": {
     *     "action": "GET",
     *     "brokerId": "1l"
     *   },
     *   "unknowns": [
     *     "data.accountState"
     *   ]
     * }
     */
    @Data
    @Builder
    static class OpaQuery {
        String query;
        OpaInput input;
        List<String> unknowns;

        String toJson() throws JsonProcessingException {
            return new ObjectMapper().writeValueAsString(this);
        }
    }

    @Data
    static class OpaInput {
        String action;
        String brokerId;

        public OpaInput(String action, String brokerId) {
            this.action = action;
            this.brokerId = brokerId;
        }
    }
}
