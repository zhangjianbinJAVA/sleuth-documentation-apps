package io.spring.cloud.sleuth.docs.service1;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@RestController
@SuppressWarnings("Duplications")
public class Application {

	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Autowired RestTemplate restTemplate;
	@Autowired Tracer tracer;
	@Value("${service2.address:localhost:8082}") String serviceAddress;

	@RequestMapping("/start")
	public String start() throws InterruptedException {
		log.info("Hello from service1. Setting baggage foo=>bar");
		String secretBaggage = tracer.getCurrentSpan().getBaggageItem("baggage");
		log.info("Super secret baggage item for key [baggage] is [" + secretBaggage + "]");
		if (StringUtils.hasText(secretBaggage)) {
			tracer.getCurrentSpan().logEvent("secret_baggage_received");
			tracer.addTag("baggage", secretBaggage);
		}
		tracer.getCurrentSpan().setBaggageItem("foo", "bar");
		log.info("Hello from service1. Calling service2");
		String response = restTemplate.getForObject("http://" + serviceAddress + "/foo", String.class);
		Thread.sleep(100);
		log.info("Got response from service2 [{}]", response);
		log.info("Service1: Baggage for [foo] is [" + tracer.getCurrentSpan().getBaggageItem("foo") + "]");
		return response;
	}

	@RequestMapping("/readtimeout")
	public String timeout() throws InterruptedException {
		Span span = this.tracer.createSpan("first_span");
		try {
			Thread.sleep(300);
			log.info("Hello from service1. Calling service2 - should end up with read timeout");
			String response = restTemplate.getForObject("http://" + serviceAddress + "/readtimeout", String.class);
			log.info("Got response from service2 [{}]", response);
			return response;
		} catch (Exception e) {
			log.error("Exception occurred while trying to send a request to service 2", e);
			throw e;
		} finally {
			this.tracer.close(span);
		}
	}

	@Bean
	RestTemplate restTemplate() {
		return new RestTemplate();
	}

	public static void main(String... args) {
		new SpringApplication(Application.class).run(args);
	}
}
