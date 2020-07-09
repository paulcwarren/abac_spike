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
	private AccountStateRepository repo;

	@Autowired
	private AccountStateStore store;

	private AccountState doc;

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

			Context("when account statements are added", () -> {

				BeforeEach(() -> {

					// add a document to each tenant with content
					{
						json = given()
								.header("X-ABAC-Context", "brokerId = foo")
								.header("content-type", "application/hal+json")
								.body("{\"brokerId\":\"foo\",\"name\":\"zzz\",\"type\":\"sop_document\"}")
								.post("/accountStates/")
								.then()
								.statusCode(HttpStatus.SC_CREATED)
								.extract().jsonPath();

						tenantFooDoc1 = (String) json.get("_links.self.href");

						given()
								.config(RestAssured.config()
										.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
								.header("X-ABAC-Context", "brokerId = foo")
								.header("content-type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
								.body(IOUtils.toByteArray(this.getClass().getResourceAsStream("/sample-docx.docx")))
								.post(tenantFooDoc1)
								.then()
								.statusCode(HttpStatus.SC_CREATED);

						json = given()
								.header("X-ABAC-Context", "brokerId = bar")
								.header("content-type", "application/hal+json")
								.body("{\"brokerId\":\"bar\",\"name\":\"www\",\"type\":\"sop_document\"}")
								.post("/accountStates/")
								.then()
								.statusCode(HttpStatus.SC_CREATED)
								.extract().jsonPath();

						tenantBarDoc1 = (String) json.get("_links.self.href");
						given()
								.config(RestAssured.config()
										.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
								.header("X-ABAC-Context", "brokerId = bar")
								.header("content-type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
								.body(IOUtils.toByteArray(this.getClass().getResourceAsStream("/sample-docx.docx")))
								.post(tenantBarDoc1)
								.then()
								.statusCode(HttpStatus.SC_CREATED);

						json = given()
								.header("X-ABAC-Context", "brokerId = foo")
								.header("content-type", "application/hal+json")
								.body("{\"brokerId\":\"foo\",\"name\":\"ppp\",\"type\":\"sop_document\"}")
								.post("/accountStates/")
								.then()
								.statusCode(HttpStatus.SC_CREATED)
								.extract().jsonPath();

						tenantFooDoc2 = (String) json.get("_links.self.href");
						given()
								.config(RestAssured.config()
										.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
								.header("X-ABAC-Context", "brokerId = foo")
								.header("content-type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
								.body(IOUtils.toByteArray(this.getClass().getResourceAsStream("/sample-docx.docx")))
								.post(tenantFooDoc2)
								.then()
								.statusCode(HttpStatus.SC_CREATED);

						json = given()
								.header("X-ABAC-Context", "brokerId = foo")
								.header("content-type", "application/hal+json")
								.body("{\"brokerId\":\"foo\",\"name\":\"aaa\",\"type\":\"sop_document\"}")
								.post("/accountStates/")
								.then()
								.statusCode(HttpStatus.SC_CREATED)
								.extract().jsonPath();

						tenantFooDoc3 = (String) json.get("_links.self.href");
						given()
								.config(RestAssured.config()
										.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
								.header("X-ABAC-Context", "brokerId = foo")
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

					Context("given a findAll for account states executed by broker foo", () -> {

						BeforeEach(() -> {
							json = given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "brokerId = foo")
									.get("/accountStates?page=0&size=2&sort=name&name.dir=asc")
									.then()
									.statusCode(HttpStatus.SC_OK)
									.extract().jsonPath();

						});

						It("should only return account statements owned by broker foo", () -> {
							int count = json.getList("_embedded.accountStates").size();
							assertThat(count, is(2));
							String previousName = "";
							for (int i = 0; i < count; i++) {
								assertThat(json.getString(format("_embedded.accountStates[%s].brokerId", i)), is("foo"));
								assertThat(json.getString(format("_embedded.accountStates[%s].type", i)), is("sop_document"));
								assertThat(json.getString(format("_embedded.accountStates[%s].name", i)), is(greaterThanOrEqualTo(previousName)));
								previousName = json.getString(format("_embedded.accountStates[%s].name", i));
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

					Context("given a custom findByXYZ is executed by broker foo", () -> {

						BeforeEach(() -> {
							json = given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "brokerId = foo")
									.header("accept", "application/hal+json")
									.get("/accountStates/search/findByType?type=sop_document&page=0&size=2&sort=name&name.dir=asc")
									.then()
									.statusCode(HttpStatus.SC_OK)
									.extract().jsonPath();

						});

						It("should only return account statements owned by broker foo", () -> {
							int count = json.getList("_embedded.accountStates").size();
							assertThat(count, is(2));
							String previousName = "";
							for (int i = 0; i < count; i++) {
								assertThat(json.getString(format("_embedded.accountStates[%s].brokerId", i)), is("foo"));
								assertThat(json.getString(format("_embedded.accountStates[%s].type", i)), is("sop_document"));
								assertThat(json.getString(format("_embedded.accountStates[%s].name", i)), is(greaterThanOrEqualTo(previousName)));
								previousName = json.getString(format("_embedded.accountStates[%s].name", i));
							}
						});
					});

				});

				Context("@Query methods", () -> {

					Context("when a custom query method is executed by broker foo", () -> {

						BeforeEach(() -> {
							json = given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "brokerId = foo")
									.header("accept", "application/hal+json")
									.get("/accountStates/search/byType?type=sop_document")
									.then()
									.statusCode(HttpStatus.SC_OK)
									.extract().jsonPath();
						});

						It("should only return account statements owned by broker foo", () -> {
							int count = json.getList("_embedded.accountStates").size();
							for (int i = 0; i < count; i++) {
								assertThat(json.getString(format("_embedded.accountStates[%s].brokerId", i)), is("foo"));
								assertThat(json.getString(format("_embedded.accountStates[%s].type", i)), is("sop_document"));
							}
						});
					});
				});

				Context("#save", () -> {

					Context("when a broker updates an account statement they own", () -> {

						It("should succeed", () -> {

							int statusCode = given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "brokerId = foo")
									.header("Content-Type", "application/hal+json")
									.body("{\"name\":\"zzz updated\"}")
									.put(tenantFooDoc1)
									.then()
									.statusCode(HttpStatus.SC_OK)
									.extract().statusCode();

							assertThat(statusCode, is(200));

							Optional<AccountState> one = repo.findById(Long.parseLong(StringUtils.substringAfter(tenantFooDoc1, "/accountStates/")));
							assertThat(one.get().getName(), is("zzz updated"));
						});
					});

					Context("when a broker updates an account statement they do not own", () -> {

						It("should fail with a 404", () -> {

							given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "brokerId = bar")
									.header("Content-Type", "application/hal+json")
									.body("{\"name\":\"zzz updated\"}")
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
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "brokerId = foo")
									.header("Accept", "application/hal+json")
									.delete(tenantFooDoc1)
									.then()
									.statusCode(HttpStatus.SC_NO_CONTENT);

							Optional<AccountState> one = repo.findById(Long.parseLong(StringUtils.substringAfter(tenantFooDoc1, "/accountStates/")));
							assertThat(one.isPresent(), is(false));
						});
					});

					Context("when a broker deletes an account statement they do not own", () -> {

						It("should fail with a 404", () -> {

							given()
									.config(RestAssured.config()
											.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
									.header("X-ABAC-Context", "brokerId = bar")
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
