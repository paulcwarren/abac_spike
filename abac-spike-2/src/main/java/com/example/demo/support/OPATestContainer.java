package com.example.demo.support;

import static java.time.temporal.ChronoUnit.SECONDS;

import java.time.Duration;
import java.util.Collections;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

public class OPATestContainer extends GenericContainer<OPATestContainer> {

    private static final String CONNECTION_URL = "http://%s:%d"; //8181
    private static final DockerImageName IMAGE_NAME = DockerImageName.parse("openpolicyagent/opa").withTag("0.20.5");

    private OPATestContainer() {
        super(IMAGE_NAME);
        withClasspathResourceMapping("/policies","/policies",BindMode.READ_ONLY);
        setCommand("run --server --addr :8181 --log-level debug --bundle /policies");
        setExposedPorts(Collections.singletonList(8181));
        waitStrategy = new LogMessageWaitStrategy()
                .withRegEx(".*Initializing server.*")
                .withStartupTimeout(Duration.of(60, SECONDS));
        start();
    }

    public static String opaURL() {
        String url = String.format(
                CONNECTION_URL,
                Singleton.INSTANCE.getContainerIpAddress(),
                Singleton.INSTANCE.getMappedPort(8181));

        return url;
    }

    @SuppressWarnings("unused") // Serializable safe singleton usage
    protected OPATestContainer readResolve() {
        return Singleton.INSTANCE;
    }

    private static class Singleton {
        private static final OPATestContainer INSTANCE = new OPATestContainer();
    }
}
