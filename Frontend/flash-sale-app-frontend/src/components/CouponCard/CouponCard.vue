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
        <button 
          class="btn btn-primary" 
          style="min-width: 100px" 
          @click="buyCoupon" 
          :disabled="!user || isProcessing || purchasedCoupons.includes(coupon.id)"
        >
          Buy
        </button>
      </div>
    </div>
    <!-- Snackbar Component -->
    <Snackbar ref="snackbar" />
  </div>
</template>

<script>
import Snackbar from "../Snackbar/Snackbar.vue";

export default {
  components: { Snackbar },
  props: ["coupon", "user"],
  data() {
    return {
      stompClient: null,
      reconnectAttempts: 0,
      snackbarQueue: [], 
      snackbarTimeout: null, 
      isProcessing: false, // ✅ Tracks if a payment is being processed
      purchasedCoupons: [], // ✅ Stores IDs of successfully purchased coupons
    };
  },
  watch: {
    // ✅ Whenever the user logs in, fetch purchased coupons
    user(newUser) {
      if (newUser) {
        this.fetchPurchasedCoupons();
      }
    }
  },
  methods: {
    async buyCoupon() {
      if (!this.user) {
        this.queueSnackbar("Please login first.", "red", 3000);
        return;
      }

      this.isProcessing = true; // ✅ Disable all Buy buttons

      try {
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
          this.queueSnackbar("Order placed! Waiting for payment processing...", "green", 2000);
          this.initializeWebSocket(data.data);
        } else {
          throw new Error("Order ID missing from response");
        }
      } catch (error) {
        console.error("Error purchasing coupon:", error);
        this.queueSnackbar(`Error: ${JSON.parse(error.message).message}`, "red", 3000);
        this.isProcessing = false; // ✅ Re-enable buttons if the purchase fails
      }
    },

    async fetchPurchasedCoupons() {
      try {
        const token = localStorage.getItem("token");
        if (!this.user || !token) return;

        const response = await fetch(`http://localhost:8080/api/users/${this.user.id}/coupons`, {
          method: "GET",
          headers: {
            Authorization: token,
            "Content-Type": "application/json",
          },
        });

        if (!response.ok) {
          throw new Error(`Failed to fetch coupons: ${response.status}`);
        }

        const data = await response.json();
        this.purchasedCoupons = data.data
          .filter((coupon) => coupon.paymentSuccessful) // ✅ Keep only successfully purchased coupons
          .map((coupon) => coupon.id); // ✅ Store their IDs

        console.log("Purchased Coupons:", this.purchasedCoupons);
      } catch (error) {
        console.error("Error fetching purchased coupons:", error);
      } finally {
        this.isProcessing = false; // ✅ Re-enable buttons once payment status is updated
      }
    },

    initializeWebSocket(orderId) {
      console.log("🔄 Initializing Native WebSocket...");

      const socket = new WebSocket(`ws://localhost:8080/ws/orders/${orderId}`);

      socket.onopen = () => {
        console.log("✅ WebSocket connected!");
        this.reconnectAttempts = 0;
      };

      socket.onmessage = async (event) => {
        setTimeout(async () => {
          console.log("🔥 Message Received from WebSocket:", event.data);
          try {
            const orderStatus = JSON.parse(event.data);
            this.queueSnackbar(orderStatus.message, "blue", 2000);

            if (orderStatus.message === "Payment successful!") {
              await this.fetchPurchasedCoupons(); // ✅ Fetch updated purchase status
              this.$emit("refresh-user-data"); // ✅ Notify parent component to refresh user data
            }
          } catch (error) {
            console.warn("⚠️ Received non-JSON message, extracting status only.");

            const statusMatch = event.data.match(/update: (.*)$/);
            const statusMessage = statusMatch ? statusMatch[1] : event.data;

            console.log("✅ Extracted Status Message:", statusMessage);
            this.queueSnackbar(statusMessage, "blue", 2000);

            if (statusMessage === "Payment successful!") {
              await this.fetchPurchasedCoupons();
              this.$emit("refresh-user-data");
            }
          }
        }, 500);
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
      const delay = Math.min(5000, Math.pow(2, this.reconnectAttempts) * 1000);

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

    queueSnackbar(message, color, duration) {
      this.snackbarQueue.push({ message, color, duration });
      if (!this.snackbarTimeout) {
        this.showNextSnackbar();
      }
    },

    showNextSnackbar() {
      if (this.snackbarQueue.length > 0 && this.$refs.snackbar) {
        const { message, color, duration } = this.snackbarQueue.shift();
        this.$refs.snackbar.showMessage(message, color, duration);

        this.snackbarTimeout = setTimeout(() => {
          this.snackbarTimeout = null;
          this.showNextSnackbar();
        }, duration + 500);
      }
    },
  },
  async mounted() {
    if (this.user) {
      await this.fetchPurchasedCoupons(); // ✅ Fetch user's purchased coupons when component loads
    }
  },
  beforeUnmount() {
    if (this.stompClient) {
      this.stompClient.close();
      console.log("🔌 WebSocket disconnected.");
    }
    if (this.snackbarTimeout) {
      clearTimeout(this.snackbarTimeout);
    }
  },
};
</script>
