package com.github.gbaso.ppb;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GreetingsController {

	@GetMapping("/hello")
	String hello() {
		return "Hello, World!";
	}
}
