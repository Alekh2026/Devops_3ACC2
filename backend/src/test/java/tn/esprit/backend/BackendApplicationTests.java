package tn.esprit.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import tn.esprit.backend.config.WebConfig;
import tn.esprit.backend.controller.EquipeController;
import tn.esprit.backend.service.IEquipeService;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class BackendApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertNotNull(context.getBean(EquipeController.class));
        assertNotNull(context.getBean(IEquipeService.class));
        assertNotNull(context.getBean(WebConfig.class));
    }
}
