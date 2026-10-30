package com.azka.notifications.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PingController {

	@GetMapping("/ping")
	public PingResponse ping() {
		return new PingResponse("ok");
	}

}
