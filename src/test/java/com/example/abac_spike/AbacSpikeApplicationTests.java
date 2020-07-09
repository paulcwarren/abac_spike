package com.example.abac_spike;

import com.github.paulcwarren.ginkgo4j.Ginkgo4jConfiguration;
import com.github.paulcwarren.ginkgo4j.Ginkgo4jSpringRunner;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.path.json.JsonPath;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import javax.persistence.EntityManager;

import java.util.Optional;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.*;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@RunWith(Ginkgo4jSpringRunner.class)
@Ginkgo4jConfiguration(threads=1)
@SpringBootTest(classes = {AbacSpikeApplication.class}, webEnvironment= SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AbacSpikeApplicationTests {

	@LocalServerPort
	int port;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private EntityManager em;

	@Autowired
	private DocumentRepository repo;

	@Autowired
	private DocumentStore store;

	private Document doc;

	private JsonPath json;

	private String tenantFooDoc1;
	private String tenantFooDoc2;
	private String tenantFooDoc3;

	private String tenantBarDoc1;

	{
		Describe("ABAC Example", () -> {
			BeforeEach(() -> {
				RestAssured.port = port;
			});

			Context("when documents are added to tenants", () -> {

				BeforeEach(() -> {

					// add a document to each tenant with content
					{
						json = given()
								.header("X-ABAC-Context", "tenantId = foo")
								.header("content-type", "application/hal+json")
								.body("{\"tenantId\":\"foo\",\"name\":\"zzz\",\"type\":\"sop_document\"}")
								.post("/documents/")
								.then()
								.statusCode(HttpStatus.SC_CREATED)
								.extract().jsonPath();

						tenantFooDoc1 = (String) json.get("_links.self.href");

						given()
								.config(RestAssured.config()
										.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
								.header("X-ABAC-Context", "tenantId = foo")
								.header("content-type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
								.body(IOUtils.toByteArray(this.getClass().getResourceAsStream("/sample-docx.docx")))
								.post(tenantFooDoc1)
								.then()
								.statusCode(HttpStatus.SC_CREATED);

						json = given()
								.header("X-ABAC-Context", "tenantId = bar")
								.header("content-type", "application/hal+json")
								.body("{\"tenantId\":\"bar\",\"name\":\"www\",\"type\":\"sop_document\"}")
								.post("/documents/")
								.then()
								.statusCode(HttpStatus.SC_CREATED)
								.extract().jsonPath();

						tenantBarDoc1 = (String) json.get("_links.self.href");
						given()
								.config(RestAssured.config()
										.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
								.header("X-ABAC-Context", "tenantId = bar")
								.header("content-type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
								.body(IOUtils.toByteArray(this.getClass().getResourceAsStream("/sample-docx.docx")))
								.post(tenantBarDoc1)
								.then()
								.statusCode(HttpStatus.SC_CREATED);

						json = given()
								.header("X-ABAC-Context", "tenantId = foo")
								.header("content-type", "application/hal+json")
								.body("{\"tenantId\":\"foo\",\"name\":\"ppp\",\"type\":\"sop_document\"}")
								.post("/documents/")
								.then()
								.statusCode(HttpStatus.SC_CREATED)
								.extract().jsonPath();

						tenantFooDoc2 = (String) json.get("_links.self.href");
						given()
								.config(RestAssured.config()
										.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
								.header("X-ABAC-Context", "tenantId = foo")
								.header("content-type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
								.body(IOUtils.toByteArray(this.getClass().getResourceAsStream("/sample-docx.docx")))
								.post(tenantFooDoc2)
								.then()
								.statusCode(HttpStatus.SC_CREATED);

						json = given()
								.header("X-ABAC-Context", "tenantId = foo")
								.header("content-type", "application/hal+json")
								.body("{\"tenantId\":\"foo\",\"name\":\"aaa\",\"type\":\"sop_document\"}")
								.post("/documents/")
								.then()
								.statusCode(HttpStatus.SC_CREATED)
								.extract().jsonPath();

						tenantFooDoc3 = (String) json.get("_links.self.href");
						given()
								.config(RestAssured.config()
										.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
								.header("X-ABAC-Context", "tenantId = foo")
								.header("content-type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
								.body(IOUtils.toByteArray(this.getClass().getResourceAsStream("/sample-docx.docx")))
								.post(tenantFooDoc3)
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

				Context("#findAll", () -> {

					Context("when a findAll query is executed by a user from tenant foo", () -> {

						BeforeEach(() -> {
							json = given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "tenantId = foo")
									.get("/documents?page=0&size=2&sort=name&name.dir=asc")
									.then()
									.statusCode(HttpStatus.SC_OK)
									.extract().jsonPath();

						});

						It("should only return results for tenant foo", () -> {
							int count = json.getList("_embedded.documents").size();
							assertThat(count, is(2));
							String previousName = "";
							for (int i = 0; i < count; i++) {
								assertThat(json.getString(format("_embedded.documents[%s].tenantId", i)), is("foo"));
								assertThat(json.getString(format("_embedded.documents[%s].type", i)), is("sop_document"));
								assertThat(json.getString(format("_embedded.documents[%s].name", i)), is(greaterThanOrEqualTo(previousName)));
								previousName = json.getString(format("_embedded.documents[%s].name", i));
							}
						});
					});

//				Context("when a findAll method is executed to get entities outside of all tenants", () -> {
//
//					BeforeEach(() -> {
//						json = 	given()
//								.config(RestAssured.config()
//										.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
//								.header("accept", "application/hal+json")
//								.get("/otherDocuments/")
//								.then()
//								.statusCode(HttpStatus.SC_OK)
//								.extract().jsonPath();
//
//					});
//
//					It("should only return results for other documents", () -> {
//						int count = json.getList("_embedded.otherDocuments").size();
//						for (int i=0; i < count; i++) {
//							assertThat(json.getString(format("_embedded.otherDocuments[%s].type", i)), is("other"));
//						}
//					});
//
//				});
				});

				Context("#findBy methods", () -> {

					Context("when a custom findByXYZ method is executed by a user from tenant foo", () -> {

						BeforeEach(() -> {
							json = given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "tenantId = foo")
									.header("accept", "application/hal+json")
									.get("/documents/search/findByType?type=sop_document&page=0&size=2&sort=name&name.dir=asc")
									.then()
									.statusCode(HttpStatus.SC_OK)
									.extract().jsonPath();

						});

						It("should only return results for tenant foo", () -> {
							int count = json.getList("_embedded.documents").size();
							assertThat(count, is(2));
							String previousName = "";
							for (int i = 0; i < count; i++) {
								assertThat(json.getString(format("_embedded.documents[%s].tenantId", i)), is("foo"));
								assertThat(json.getString(format("_embedded.documents[%s].type", i)), is("sop_document"));
								assertThat(json.getString(format("_embedded.documents[%s].name", i)), is(greaterThanOrEqualTo(previousName)));
								previousName = json.getString(format("_embedded.documents[%s].name", i));
							}
						});
					});

				});

				Context("@Query methods", () -> {

					Context("when a custom query method is executed by a user from tenant foo", () -> {

						BeforeEach(() -> {
							json = given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "tenantId = foo")
									.header("accept", "application/hal+json")
									.get("/documents/search/byType?type=sop_document")
									.then()
									.statusCode(HttpStatus.SC_OK)
									.extract().jsonPath();

						});

						It("should only return results for tenant foo", () -> {
							int count = json.getList("_embedded.documents").size();
							for (int i = 0; i < count; i++) {
								assertThat(json.getString(format("_embedded.documents[%s].tenantId", i)), is("foo"));
								assertThat(json.getString(format("_embedded.documents[%s].type", i)), is("sop_document"));
							}
						});
					});
				});

				Context("#save", () -> {

					Context("when an authorized principal updates a documents", () -> {

						It("should succeed", () -> {

							int statusCode = given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "tenantId = foo")
									.header("Content-Type", "application/hal+json")
									.body("{\"name\":\"zzz updated\"}")
									.put(tenantFooDoc1)
									.then()
									.statusCode(HttpStatus.SC_OK)
									.extract().statusCode();

							assertThat(statusCode, is(200));

							Optional<Document> one = repo.findById(Long.parseLong(StringUtils.substringAfter(tenantFooDoc1, "/documents/")));
							assertThat(one.get().getName(), is("zzz updated"));
						});
					});

					Context("when an unauthorized principal updates a documents", () -> {

						It("should succeed", () -> {

							given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "tenantId = bar")
									.header("Content-Type", "application/hal+json")
									.body("{\"name\":\"zzz updated\"}")
									.put(tenantFooDoc1)
									.then()
									.statusCode(HttpStatus.SC_NOT_FOUND);
						});
					});
				});

				Context("#delete", () -> {

					Context("when an authorized principal deletes a documents", () -> {

						It("should succeed", () -> {

							given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "tenantId = foo")
									.header("Accept", "application/hal+json")
									.delete(tenantFooDoc1)
									.then()
									.statusCode(HttpStatus.SC_NO_CONTENT);

							Optional<Document> one = repo.findById(Long.parseLong(StringUtils.substringAfter(tenantFooDoc1, "/documents/")));
							assertThat(one.isPresent(), is(false));
						});
					});

					Context("when an unauthorized principal deletes a documents", () -> {

						It("should fail with a 404", () -> {

							given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "tenantId = bar")
									.header("Accept", "application/hal+json")
									.delete(tenantFooDoc1)
									.then()
									.statusCode(HttpStatus.SC_NOT_FOUND);
						});
					});
				});
			});
		});
	}

	@Test
	public void noop() {}
}
