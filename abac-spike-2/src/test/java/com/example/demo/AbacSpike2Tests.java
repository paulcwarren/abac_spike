package com.example.demo;

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
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.auth.BasicUserPrincipal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.versions.VersionInfo;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.paulcwarren.ginkgo4j.Ginkgo4jConfiguration;
import com.github.paulcwarren.ginkgo4j.Ginkgo4jSpringRunner;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.path.json.JsonPath;

import be.heydari.AstWalker;
import be.heydari.lib.converters.protobuf.ProtobufUtils;
import be.heydari.lib.converters.protobuf.generated.PDisjunction;
import be.heydari.lib.expressions.Disjunction;
import lombok.Builder;
import lombok.Data;

@RunWith(Ginkgo4jSpringRunner.class)
@Ginkgo4jConfiguration(threads = 1)
@SpringBootTest(classes = {
        ABACSpike2Application.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AbacSpike2Tests {

    @LocalServerPort
    int port;

    @Autowired
    private AccountStateRepository repo;

    @Autowired
    private AccountStateStore store;

    @Autowired
    private BrokerRepository brokerRepo;

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
        Describe("ABAC Spike 2 Tests", () -> {

            BeforeEach(() -> {

                RestAssured.port = port;

                // add some brokers
                {
                    json = given()
                            .auth().preemptive().basic("tuser", "tuser")
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
                            .auth().preemptive().basic("tuser", "tuser")
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
                            .auth().preemptive().basic("tuser", "tuser")
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
                            .get("_links.content.href");

                    json = given()
                            .auth().preemptive().basic("tuser", "tuser")
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
                    .auth().preemptive().basic("tuser", "tuser")
                    .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                            .substringAfter(brokerFooUri, "/brokers/"))))
                    .header("content-type", "text/plain")
                    .body(IOUtils
                            .toByteArray("foo doc 1"))
                    .post(tenantFooDoc1Content)
                    .then()
                    .statusCode(HttpStatus.SC_CREATED);

                    json = given()
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/"))))
                            .auth().preemptive().basic("tuser", "tuser")
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
                            .get("_links.content.href");

                    json = given()
                            .auth().preemptive().basic("tuser", "tuser")
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
                    .auth().preemptive().basic("tuser", "tuser")
                    .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                            .substringAfter(brokerBarUri, "/brokers/"))))
                    .header("content-type", "text/plain")
                    .body(IOUtils
                            .toByteArray("bar doc 1"))
                    .post(tenantBarDoc1Content)
                    .then()
                    .statusCode(HttpStatus.SC_CREATED);

                    json = given()
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerFooUri, "/brokers/"))))
                            .auth().preemptive().basic("tuser", "tuser")
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
                            .get("_links.content.href");

                    json = given()
                            .auth().preemptive().basic("tuser", "tuser")
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
                    .auth().preemptive().basic("tuser", "tuser")
                    .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                            .substringAfter(brokerFooUri, "/brokers/"))))
                    .header("content-type", "text/plain")
                    .body(IOUtils
                            .toByteArray("foo doc 2"))
                    .post(tenantFooDoc2Content)
                    .then()
                    .statusCode(HttpStatus.SC_CREATED);

                    json = given()
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerFooUri, "/brokers/"))))
                            .auth().preemptive().basic("tuser", "tuser")
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
                            .get("_links.content.href");

                    json = given()
                            .auth().preemptive().basic("tuser", "tuser")
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
                    .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                            .substringAfter(brokerFooUri, "/brokers/"))))
                    .auth().preemptive().basic("tuser", "tuser")
                    .header("content-type", "text/plain")
                    .body(IOUtils
                            .toByteArray("foo doc 3"))
                    .post(tenantFooDoc3Content)
                    .then()
                    .statusCode(HttpStatus.SC_CREATED);
                }

                // add some other documents
//                {
//                    given()
//                    .header("content-type", "application/hal+json")
//                    .body("{\"type\":\"other\"}")
//                    .post("/otherDocuments/")
//                    .then()
//                    .statusCode(HttpStatus.SC_CREATED);
//
//                    given()
//                    .header("content-type", "application/hal+json")
//                    .body("{\"type\":\"other\"}")
//                    .post("/otherDocuments/")
//                    .then()
//                    .statusCode(HttpStatus.SC_CREATED);
//                }
            });

            Context("findAll", () -> {

                Context("given a findAll for account states executed by broker foo", () -> {

                    BeforeEach(() -> {
                        json = given()
                                .config(RestAssured
                                        .config()
                                        .encoderConfig(encoderConfig()
                                                .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                                .auth().preemptive().basic("tuser", "tuser")
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

                It("should apply the given abac context", () -> {

                    json = given()
                            .config(RestAssured
                                    .config()
                                    .encoderConfig(encoderConfig()
                                            .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                            .auth().preemptive().basic("tuser", "tuser")
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerFooUri, "/brokers/"))))
//                        .get("/accountStates?page=0&size=2&sort=name&name.dir=asc")
                            .get("/accountStates?name=www")
                            .then()
                            .statusCode(HttpStatus.SC_OK)
                            .extract()
                            .jsonPath();

                    List accountStates = json.get("_embedded.accountStates");
                    assertThat(accountStates.size(), is(0));

                    json = given()
                            .config(RestAssured
                                    .config()
                                    .encoderConfig(encoderConfig()
                                            .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                            .auth().preemptive().basic("tuser", "tuser")
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/"))))
//                        .get("/accountStates?page=0&size=2&sort=name&name.dir=asc")
                            .get("/accountStates?name=www")
                            .then()
                            .statusCode(HttpStatus.SC_OK)
                            .extract()
                            .jsonPath();

                    String name = json.get("_embedded.accountStates[0].name");
                    assertThat(name, is("www"));
                });
            });

            Context("findById", () -> {

                It("should apply the given abac context", () -> {

                    given()
                            .config(RestAssured
                                    .config()
                                    .encoderConfig(encoderConfig()
                                            .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                            .auth().preemptive().basic("tuser", "tuser")
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerFooUri, "/brokers/"))))
                            .header("accept", "application/hal+json")
//                        .get("/accountStates?page=0&size=2&sort=name&name.dir=asc")
                            .get("/accountStates/" + StringUtils.substringAfter(tenantBarDoc1, "/accountStates/"))
                            .then()
                            .statusCode(HttpStatus.SC_NOT_FOUND);

                    given()
                            .config(RestAssured
                                    .config()
                                    .encoderConfig(encoderConfig()
                                            .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                            .auth().preemptive().basic("tuser", "tuser")
                            .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                    .substringAfter(brokerBarUri, "/brokers/"))))
                            .header("accept", "application/hal+json")
//                        .get("/accountStates?page=0&size=2&sort=name&name.dir=asc")
                            .get("/accountStates/" + StringUtils.substringAfter(tenantBarDoc1, "/accountStates/"))
                            .then()
                            .statusCode(HttpStatus.SC_OK);
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
                                .auth().preemptive().basic("tuser", "tuser")
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

            Context("#save", () -> {

                Context("when a broker updates an account statement they own", () -> {

                    It("should succeed", () -> {

                        int statusCode = given()
                                .config(RestAssured
                                        .config()
                                        .encoderConfig(encoderConfig()
                                                .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                                .auth().preemptive().basic("tuser", "tuser")
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
                        .auth().preemptive().basic("tuser", "tuser")
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
                        .auth().preemptive().basic("tuser", "tuser")
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
                        .auth().preemptive().basic("tuser", "tuser")
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
                                .auth().preemptive().basic("tuser", "tuser")
                                .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                        .substringAfter(brokerFooUri, "/brokers/"))))
                                .header("Accept", "text/plain")
                                .auth().basic("tuser", "")
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
                        .auth().preemptive().basic("tuser", "tuser")
                        .header("X-ABAC-Context", queryOPA(Long.valueOf(StringUtils
                                .substringAfter(brokerBarUri, "/brokers/"))))
                        .header("Accept", "text/plain")
                        .get(tenantFooDoc1Content)
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
                                .auth().preemptive().basic("tuser", "tuser")
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
                        .auth().preemptive().basic("tuser", "tuser")
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
                        .auth().preemptive().basic("tuser", "tuser")
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

                Context("when a broker gets a version of an account state they do not own", () -> {

                    It("should fail with a 404", () -> {

                        given()
                        .config(RestAssured
                                .config()
                                .encoderConfig(encoderConfig()
                                        .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                        .auth().preemptive().basic("tuser", "tuser")
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
                                .auth().preemptive().basic("tuser", "tuser")
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

    @Test
    public void noop() {}
}
