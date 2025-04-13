package com.github.gbaso.ppb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(GreetingsController.class)
@Import(SecurityConfiguration.WithSecurityFilterChain.class)
class MockWithSecurityFilterChainTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void hello() {
		assertThat(mvc.get().uri("/hello"))
			.hasStatusOk();
	}

}
