package Com.SimpleFlashSaleBackend.SimpleFlashSale.Controller;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendOrderUpdate(Long orderId, String message) {
        // Send real-time update to the frontend
        messagingTemplate.convertAndSend("/topic/order-status/" + orderId, message);
    }
}