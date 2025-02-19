<template>
  <div class="container">
    <header class="d-flex justify-content-between align-items-center p-3 bg-light">
      <h3>Flash Sale</h3>

      <div>
        <!-- Login Button -->
        <button v-if="!user" class="btn btn-primary" data-bs-toggle="modal" data-bs-target="#loginModal">
          Login
        </button>

        <!-- Username Button & Logout -->
        <div v-else class="d-flex align-items-center gap-3">
          <!-- User Credit Display -->
          <p class="mb-0 fw-bold text-success">💰 {{ user.credit }}</p>

          <!-- User Profile Button -->
          <button class="btn btn-outline-primary" @click="showUserCoupons">
            👤 {{ user.username }}
          </button>

          <!-- Logout Button -->
          <button class="btn btn-danger" @click="logout">🚪 Logout</button>
        </div>
      </div>
    </header>

    <!-- Coupons List -->
    <div>
      <CouponCard v-for="coupon in coupons" :key="coupon.id" :coupon="coupon" :user="user"
        :purchasedCoupons="purchasedCoupons" @update-purchased-coupons="fetchPurchasedCoupons"
        @update-user-credit="fetchUserCredit" />
    </div>

    <!-- Footer -->
    <footer class="text-center p-3 bg-light">2025 @Lisa White</footer>

    <!-- Components -->
    <LoginPopup @login-success="setUser" />
    <UserCouponsPopup v-if="showCouponsPopup" :user="user" @close="showCouponsPopup = false" />
  </div>
</template>

<script>
import CouponCard from "./components/CouponCard/CouponCard.vue";
import LoginPopup from "./components/LoginPopup/LoginPopup.vue";
import UserCouponsPopup from "./components/UserCouponsPopup/UserCouponsPopup.vue";

export default {
  components: { CouponCard, LoginPopup, UserCouponsPopup },
  data() {
    return {
      user: null,
      coupons: [],
      purchasedCoupons: [],
      showCouponsPopup: false,
      token: null,
    };
  },

  async mounted() {
    this.restoreSession();
    this.fetchCoupons();
  },

  methods: {
    async setUser(loginResponse) {
      // console.log(loginResponse);
      if (!loginResponse || !loginResponse.data || typeof loginResponse.data !== "string") {
        return;
      }

      this.token = loginResponse.data.trim(); // ✅ Ensure token is a string
      localStorage.setItem("token", this.token);

      try {
        // ✅ Decode JWT Safely
        console.log(this.token);
        const tokenParts = this.token.split(".");
        if (tokenParts.length !== 3) throw new Error("Invalid token format");

        const decodedPayload = JSON.parse(atob(tokenParts[1])); // Decode JWT payload
        const userId = decodedPayload.sub;

        if (!userId) throw new Error("User ID missing in token");

        localStorage.setItem("userId", userId);
        console.log("🔑 Token & userId stored in localStorage:", userId);

        await this.verifySession(userId);
      } catch (error) {
        console.error("Error decoding JWT:", error);
        this.logout();
      }
    },

    async restoreSession() {
      const storedToken = localStorage.getItem("token");
      const storedUserId = localStorage.getItem("userId");

      if (storedToken && storedUserId) {
        this.token = storedToken;
        console.log("🔄 Restoring session...");
        await this.verifySession(storedUserId);
      }
    },

    async verifySession(userId) {
      try {
        const response = await fetch(`http://localhost:8080/api/users/${userId}`, {
          headers: { Authorization: this.token },
        });

        if (!response.ok) {
          throw new Error(`Session verification failed: ${response.status}`);
        }

        const userData = await response.json();
        this.user = userData.data;
        console.log("✅ Session verified:", this.user);

        await this.fetchPurchasedCoupons();
      } catch (error) {
        console.error("❌ Invalid session:", error);
        this.logout();
      }
    },

    async fetchCoupons() {
      try {
        const response = await fetch("http://localhost:8080/api/coupons?page=0&size=8");
        if (!response.ok) throw new Error(`Failed to fetch coupons: ${response.status}`);

        const jsonData = await response.json();
        this.coupons = jsonData.data || [];
      } catch (error) {
        console.error("Error fetching coupons:", error);
      }
    },

    async fetchPurchasedCoupons() {
      if (!this.user) return;
      try {
        const token = localStorage.getItem("token");
        const response = await fetch(
          `http://localhost:8080/api/users/${this.user.id}/coupons`,
          { method: "GET", headers: { Authorization: token } }
        );

        if (!response.ok) throw new Error(`Failed to fetch: ${response.status}`);

        const data = await response.json();
        this.purchasedCoupons = data.data
          .filter(coupon => coupon.paymentSuccessful)
          .map(coupon => coupon.id);

        console.log("✅ Purchased Coupons Updated:", this.purchasedCoupons);
      } catch (error) {
        console.error("❌ Error fetching purchased coupons:", error);
      }
    },

    async fetchUserCredit() {
      if (!this.user) return;
      try {
        const response = await fetch(`http://localhost:8080/api/users/${this.user.id}`, {
          headers: { Authorization: this.token },
        });

        if (!response.ok) throw new Error(`Failed to fetch user: ${response.status}`);

        const userData = await response.json();
        this.user.credit = userData.data.credit; // ✅ Update the displayed credit
        console.log("✅ User credit updated:", this.user.credit);
      } catch (error) {
        console.error("❌ Error fetching user credit:", error);
      }
    },

    async logout() {
      localStorage.removeItem("token");
      localStorage.removeItem("userId");
      this.user = null;
      this.token = null;
      this.purchasedCoupons = [];
      console.log("🚪 User logged out!");
    },

    showUserCoupons() {
      this.showCouponsPopup = true;
    },
  },
};
</script>
