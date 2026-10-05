package com.saurabh.stockdecision.jev;

import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.api-key=test-key", "typesafe.api-key=ts-test-key", "typesafe.model=jev-preview"})
class JevConfigTest {

    @Autowired
    private TypeSafeClient typeSafeClient;

    @Autowired
    private JevClient jevClient;

    @Test
    void createsJevClientWhenKeyIsSet() {
        assertThat(jevClient).isNotNull();
        assertThat(typeSafeClient.defaultModel()).isEqualTo("jev-preview");
    }
}
