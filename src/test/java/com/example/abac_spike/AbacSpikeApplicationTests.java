package com.example.abac_spike;

import com.github.paulcwarren.ginkgo4j.Ginkgo4jConfiguration;
import com.github.paulcwarren.ginkgo4j.Ginkgo4jSpringRunner;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.path.json.JsonPath;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import javax.persistence.EntityManager;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.*;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
	{
		Describe("ABAC Example", () -> {
			BeforeEach(() -> {
				RestAssured.port = port;
			});

			Context("when documents with content are added to each tenant", () -> {

				BeforeEach(() -> {

					JsonPath json = given()
							.header("content-type", "application/hal+json")
							.body("{\"tenantId\":\"foo\"}")
							.post("/documents/")
							.then()
							.statusCode(HttpStatus.SC_CREATED)
							.extract().jsonPath();

					String chapter1Uri = json.get("_links.self.href");

					json = given()
							.header("content-type", "application/hal+json")
							.body("{\"tenantId\":\"bar\"}")
							.post("/documents/")
							.then()
							.statusCode(HttpStatus.SC_CREATED)
							.extract().jsonPath();

					String chapter2Uri = json.get("_links.self.href");

					given()
							.config(RestAssured.config()
									.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
							.header("content-type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
							.header("X-ABAC-Context", "tenantId = foo")
							.body(IOUtils.toByteArray(this.getClass().getResourceAsStream("/sample-docx.docx")))
							.post(chapter1Uri)
							.then()
							.statusCode(HttpStatus.SC_CREATED);

					given()
							.config(RestAssured.config()
									.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
							.header("content-type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
							.header("X-ABAC-Context", "tenantId = bar")
							.body(IOUtils.toByteArray(this.getClass().getResourceAsStream("/sample-docx.docx")))
							.post(chapter2Uri)
							.then()
							.statusCode(HttpStatus.SC_CREATED);
				});

				Context("when a findAll query is executed by a user from tenant foo", () -> {

					BeforeEach(() -> {
						json = 	given()
								.config(RestAssured.config()
										.encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
								.header("X-ABAC-Context", "tenantId = foo")
								.get("/documents/")
								.then()
								.statusCode(HttpStatus.SC_OK)
								.extract().jsonPath();

					});

					It("should only return results for tenant foo", () -> {
						assertThat(json.getList("_embedded.documents").size(), is(1));
						assertThat(json.getString("_embedded.documents[0].tenantId"), is("foo"));
					});
				});
			});
		});
	}

	@Test
	public void noop() {}
}
