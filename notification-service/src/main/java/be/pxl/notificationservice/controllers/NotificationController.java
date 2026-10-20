package be.pxl.notificationservice.controllers;

import be.pxl.notificationservice.DTO.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    @PostMapping
    public void receiveNotification(@RequestBody NotificationRequest request) {
        log.info("ONTVANGEN NOTIFICATIE: {}", request.message());
    }
}
