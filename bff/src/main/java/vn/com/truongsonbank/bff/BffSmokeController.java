package vn.com.truongsonbank.bff;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

import java.util.Map;

@RestController
@ResponseWrapper
class BffSmokeController {
    @GetMapping("/smoke")
    Map<String, Object> smoke() {
        return Map.of("service", "bff", "status", "ok");
    }
}
