package vn.com.truongsonbank.sharedpackage;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.shared.cache.TsbCacheEvict;
import vn.com.truongsonbank.shared.cache.TsbCacheable;
import vn.com.truongsonbank.shared.exception.BusinessException;
import vn.com.truongsonbank.shared.exception.ErrorDescriptor;
import vn.com.truongsonbank.shared.logging.SensitiveDataMasker;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
		SharedpackageApplicationTests.TestApplication.class,
		SharedpackageApplicationTests.TestController.class,
		SharedpackageApplicationTests.CacheDemoService.class
}, properties = {
		"tsb.shared.cache.l1.ttl=10m",
		"tsb.shared.cache.l1.soft-ttl=25ms"
})
@AutoConfigureMockMvc
class SharedpackageApplicationTests {
	@Autowired
	private MockMvc mvc;

	@Autowired
	private CacheDemoService cacheDemoService;

	@Test
	void contextLoads() {
	}

	@Test
	void wrapsAnnotatedResponse() throws Exception {
		mvc.perform(get("/test/wrapped")
						.header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Trace-Id", "4bf92f3577b34da6a3ce929d0e0e4736"))
				.andExpect(jsonPath("$.traceId", is("4bf92f3577b34da6a3ce929d0e0e4736")))
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.code", is("SUCCESS")))
				.andExpect(jsonPath("$.message", is("Success")))
				.andExpect(jsonPath("$.duration", endsWith("ms")))
				.andExpect(jsonPath("$.timestamp", notNullValue()))
				.andExpect(jsonPath("$.data.name", is("son")));
	}

	@Test
	void createsTraceIdWhenTraceparentIsMissing() throws Exception {
		mvc.perform(get("/test/wrapped"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Trace-Id", notNullValue()))
				.andExpect(jsonPath("$.traceId", notNullValue()));
	}

	@Test
	void prefersMdcTraceId() throws Exception {
		MDC.put("traceId", "mdc-trace");
		try {
			mvc.perform(get("/test/wrapped")
							.header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
					.andExpect(status().isOk())
					.andExpect(header().string("X-Trace-Id", "mdc-trace"))
					.andExpect(jsonPath("$.traceId", is("mdc-trace")));
		} finally {
			MDC.clear();
		}
	}

	@Test
	void leavesUnannotatedResponseAlone() throws Exception {
		mvc.perform(get("/test/raw"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("son")))
				.andExpect(jsonPath("$.success").doesNotExist());
	}

	@Test
	void handlesBusinessExceptionWithLocalizedMessage() throws Exception {
		mvc.perform(get("/test/error")
						.header("X-Language", "vi")
						.header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.traceId", is("4bf92f3577b34da6a3ce929d0e0e4736")))
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.code", is("TEST_001")))
				.andExpect(jsonPath("$.message", is("OTP khong hop le")))
				.andExpect(jsonPath("$.duration", endsWith("ms")))
				.andExpect(jsonPath("$.timestamp", notNullValue()))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void masksSensitiveData() {
		SensitiveDataMasker masker = new SensitiveDataMasker(Map.of(
				"pin", new vn.com.truongsonbank.shared.logging.LoggingProperties.MaskRule("pin", 0, 0, "*"),
				"phone", new vn.com.truongsonbank.shared.logging.LoggingProperties.MaskRule("phone,phoneNumber,mobile,sdt", 0, 3, "*"),
				"card", new vn.com.truongsonbank.shared.logging.LoggingProperties.MaskRule("pan,cardNumber", 6, 4, "*")));

		org.assertj.core.api.Assertions.assertThat(masker.mask("pin", "123456")).isEqualTo("******");
		org.assertj.core.api.Assertions.assertThat(masker.mask("phone", "0987654321")).isEqualTo("*******321");
		org.assertj.core.api.Assertions.assertThat(masker.mask("cardNumber", "1234567890123456")).isEqualTo("123456******3456");
		org.assertj.core.api.Assertions.assertThat(masker.mask("username", "son")).isEqualTo("son");
	}

	@Test
	void cachesMethodResultInL1() {
		cacheDemoService.evict("a");
		cacheDemoService.reset();

		org.assertj.core.api.Assertions.assertThat(cacheDemoService.cached("a")).isEqualTo("a-1");
		org.assertj.core.api.Assertions.assertThat(cacheDemoService.cached("a")).isEqualTo("a-1");
		org.assertj.core.api.Assertions.assertThat(cacheDemoService.calls()).isEqualTo(1);
	}

	@Test
	void refreshesAfterSoftTtl() throws Exception {
		cacheDemoService.evict("soft");
		cacheDemoService.reset();

		org.assertj.core.api.Assertions.assertThat(cacheDemoService.cached("soft")).isEqualTo("soft-1");
		Thread.sleep(60);

		org.assertj.core.api.Assertions.assertThat(cacheDemoService.cached("soft")).isEqualTo("soft-2");
		org.assertj.core.api.Assertions.assertThat(cacheDemoService.calls()).isEqualTo(2);
	}

	@Test
	void evictsCachedValue() {
		cacheDemoService.evict("a");
		cacheDemoService.reset();

		org.assertj.core.api.Assertions.assertThat(cacheDemoService.cached("a")).isEqualTo("a-1");
		cacheDemoService.evict("a");

		org.assertj.core.api.Assertions.assertThat(cacheDemoService.cached("a")).isEqualTo("a-2");
		org.assertj.core.api.Assertions.assertThat(cacheDemoService.calls()).isEqualTo(2);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {
	}

	@RestController
	static class TestController {
		@ResponseWrapper
		@GetMapping("/test/wrapped")
		Map<String, String> wrapped() {
			return Map.of("name", "son");
		}

		@GetMapping("/test/raw")
		Map<String, String> raw() {
			return Map.of("name", "son");
		}

		@GetMapping("/test/error")
		Map<String, String> error() {
			throw new BusinessException(TestErrors.INVALID_OTP);
		}
	}

	@Service
	static class CacheDemoService {
		private final AtomicInteger calls = new AtomicInteger();

		@TsbCacheable(cacheName = "demo", key = "#p0")
		public String cached(String id) {
			return id + "-" + calls.incrementAndGet();
		}

		@TsbCacheEvict(cacheName = "demo", key = "#p0")
		public void evict(String id) {
		}

		public int calls() {
			return calls.get();
		}

		public void reset() {
			calls.set(0);
		}
	}

	enum TestErrors implements ErrorDescriptor {
		INVALID_OTP("TEST_001", 400, "Invalid OTP");

		private final String code;
		private final int httpStatus;
		private final String defaultMessage;

		TestErrors(String code, int httpStatus, String defaultMessage) {
			this.code = code;
			this.httpStatus = httpStatus;
			this.defaultMessage = defaultMessage;
		}

		@Override
		public String code() {
			return code;
		}

		@Override
		public int httpStatus() {
			return httpStatus;
		}

		@Override
		public String defaultMessage() {
			return defaultMessage;
		}
	}
}
