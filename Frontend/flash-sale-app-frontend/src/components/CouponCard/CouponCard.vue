<template>
  <div class="card d-flex flex-row p-3 m-2">
    <img :src="coupon.imageURL" class="img-fluid" style="width: 150px; height: 150px; object-fit: cover" />
    <div class="d-flex flex-column flex-grow-1 ms-3">
      <h5>{{ coupon.name }}</h5>
      <p class="text-muted">{{ coupon.description }}</p>
      <div class="mt-auto d-flex" style="justify-content: space-between">
        <p>
          <strong>Price:</strong> 💰{{ coupon.price }} | <strong>Qty:</strong>
          {{ coupon.quantity }}
        </p>
        <button class="btn btn-primary" style="min-width: 100px" @click="buyCoupon" :disabled="!user">
          Buy
        </button>
      </div>
    </div>
  </div>

  <!-- Snackbar Component -->
  <Snackbar ref="snackbar" />
</template>

<script>
import Snackbar from "../Snackbar/Snackbar.vue";

export default {
  components: { Snackbar },
  props: ["coupon", "user"],
  data() {
    return {
      stompClient: null,
      orderStatus: null,
      reconnectAttempts: 0, // Track reconnection attempts
    };
  },
  methods: {
    async buyCoupon() {
      if (!this.user) {
        this.$refs.snackbar.showMessage("Please login first.", "red", 3000);
        return;
      }

      try {
        // ✅ Convert data to x-www-form-urlencoded format
        const formData = new URLSearchParams();
        formData.append("userId", this.user.id);
        formData.append("couponId", this.coupon.id);

        const token = localStorage.getItem("token");
        const response = await fetch("http://localhost:8080/api/coupons/buy", {
          method: "POST",
          headers: {
            Authorization: token,
            "Content-Type": "application/x-www-form-urlencoded",
          },
          body: formData.toString(),
        });

        if (!response.ok) {
          const errorMessage = await response.text();
          throw new Error(errorMessage);
        }

        const data = await response.json();
        if (data.data) {
          this.$refs.snackbar.showMessage("Order placed successfully!", "green", 2000);
          this.initializeWebSocket(data.data);
        } else {
          throw new Error("Order ID missing from response");
        }
      } catch (error) {
        console.error("Error purchasing coupon:", error);
        this.$refs.snackbar.showMessage(
          `Error: ${JSON.parse(error.message).message}`,
          "red",
          3000
        );
      }
    },

    initializeWebSocket(orderId) {
      console.log("🔄 Initializing Native WebSocket...");

      const socket = new WebSocket(`ws://localhost:8080/ws/orders/${orderId}`);

      socket.onopen = () => {
        console.log("✅ WebSocket connected!");
        this.reconnectAttempts = 0;
      };

      socket.onmessage = (event) => {
        console.log("🔥 Message Received from WebSocket:", event.data);
        try {
          const orderStatus = JSON.parse(event.data);
          console.log("✅ Order Update (Parsed as JSON):", orderStatus);
          this.$refs.snackbar.showMessage(`Order Update: ${orderStatus.message}`, "blue", 2500);
        } catch (error) {
          console.warn("⚠️ Received non-JSON message, extracting status only.");

          const statusMatch = event.data.match(/update: (.*)$/);
          const statusMessage = statusMatch ? statusMatch[1] : event.data;

          console.log("✅ Extracted Status Message:", statusMessage);
          this.$refs.snackbar.showMessage(statusMessage, "blue", 2500);
        }
      };

      socket.onerror = (error) => {
        console.error("⚠️ WebSocket error:", error);
        this.handleWebSocketReconnect(orderId);
      };

      socket.onclose = () => {
        console.warn("🔌 WebSocket closed. Attempting to reconnect...");
        this.handleWebSocketReconnect(orderId);
      };

      this.stompClient = socket;
    },

    handleWebSocketReconnect(orderId) {
      const maxAttempts = 5;
      const delay = Math.min(5000, Math.pow(2, this.reconnectAttempts) * 1000); // Exponential backoff

      if (this.reconnectAttempts < maxAttempts) {
        console.warn(`🔁 Retrying WebSocket connection in ${delay / 1000}s...`);
        setTimeout(() => {
          this.reconnectAttempts++;
          this.initializeWebSocket(orderId);
        }, delay);
      } else {
        console.error("🚨 Max WebSocket reconnection attempts reached.");
      }
    },

    subscribeToOrderStatus(orderId) {
      if (this.stompClient && this.stompClient.connected) {
        console.log("✅ Subscribing to:", `/topic/order-status/${orderId}`);

        this.stompClient.subscribe(`/topic/order-status/${orderId}`, (message) => {
          console.log("🔥 Message Received from WebSocket:", message);

          try {
            const orderStatus = JSON.parse(message.body);
            console.log("✅ Order Update:", orderStatus);
            this.$refs.snackbar.showMessage(`Order Update: ${orderStatus}`, "blue", 3000);
          } catch (error) {
            console.error("❌ Error parsing WebSocket message:", error);
          }
        });
      } else {
        console.warn("⚠️ WebSocket not connected. Retrying...");
        setTimeout(() => this.subscribeToOrderStatus(orderId), 2000);
      }
    },
  },
  beforeUnmount() {
    if (this.stompClient) {
      this.stompClient.close();
      console.log("🔌 WebSocket disconnected.");
    }
  },
};
</script>
