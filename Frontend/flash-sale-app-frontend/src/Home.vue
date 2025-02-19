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
        @refresh-user-data="refreshUserData" />
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
      showCouponsPopup: false, // Controls modal visibility
      token: null, // Store token
    };
  },

  async mounted() {
    try {
      // ✅ Retain user session on refresh
      this.restoreSession();

      // ✅ Fetch coupons
      const response = await fetch("http://localhost:8080/api/coupons?page=0&size=8");

      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`);
      }

      const jsonData = await response.json();
      this.coupons = jsonData.data || [];
      console.log("Coupons fetched:", this.coupons);
    } catch (error) {
      console.error("Error fetching coupons:", error);
    }
  },

  methods: {
    async setUser(loginResponse) {
      if (!loginResponse || !loginResponse.data) {
        console.error("Invalid login response:", loginResponse);
        return;
      }

      try {
        this.token = loginResponse.data;
        localStorage.setItem("token", this.token);

        // Decode JWT to get userId
        const tokenParts = this.token.split(".");
        const decodedPayload = JSON.parse(atob(tokenParts[1]));
        const userId = decodedPayload.sub;
        localStorage.setItem("userId", userId); // ✅ Store userId in localStorage

        console.log("🔑 Token & userId stored in localStorage:", userId);

        // ✅ Fetch user profile dynamically
        await this.verifySession(userId);
      } catch (error) {
        console.error("Error fetching user profile:", error);
      }
    },

    // ✅ Restore session on refresh
    async restoreSession() {
      const storedToken = localStorage.getItem("token");
      const storedUserId = localStorage.getItem("userId");

      if (storedToken && storedUserId) {
        try {
          this.token = storedToken;
          console.log("🔄 Session restored. Verifying token...");

          // ✅ Verify token validity
          await this.verifySession(storedUserId);
        } catch (error) {
          console.error("❌ Error restoring session:", error);
          this.logout(); // ✅ Clear invalid session
        }
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
        console.log("✅ Session verified successfully! User:", this.user);
      } catch (error) {
        console.error("❌ Session invalid, logging out:", error);
        this.logout(); // ✅ Clear invalid session if request fails
      }
    },

    showUserCoupons() {
      this.showCouponsPopup = true;
    },

    async logout() {
      const token = localStorage.getItem("token");

      if (token) {
        try {
          const response = await fetch(`http://localhost:8080/api/users/logout`, {
            method: "POST",
            headers: { Authorization: token },
          });

          if (!response.ok) {
            console.warn("⚠️ Logout request failed. Clearing session anyway.");
          }
        } catch (error) {
          console.error("❌ Error logging out:", error);
        }
      }

      // ✅ Always clear session data
      localStorage.removeItem("token");
      this.user = null;
      this.token = null;

      console.log("🚪 User logged out!");
    },

    refreshUserData() {
      console.log("🔄 Refreshing user data...");
      const storedUserId = localStorage.getItem("userId");
      if (storedUserId) {
        this.verifySession(storedUserId);
      }
    },
  },
};
</script>
