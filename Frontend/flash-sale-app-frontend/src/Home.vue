<template>
  <div class="container">
    <header class="d-flex justify-content-between align-items-center p-3 bg-light">
      <h3>Flash Sale</h3>

      <div>
        <!-- Login Button -->
        <button
          v-if="!user"
          class="btn btn-primary"
          data-bs-toggle="modal"
          data-bs-target="#loginModal"
        >
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
      <CouponCard
        v-for="coupon in coupons"
        :key="coupon.id"
        :coupon="coupon"
        :user="user"
      />
    </div>

    <!-- Footer -->
    <footer class="text-center p-3 bg-light">2025 @Lisa White</footer>

    <!-- Components -->
    <LoginPopup @login-success="setUser" />
    <UserCouponsPopup
      v-if="showCouponsPopup"
      :user="user"
      @close="showCouponsPopup = false"
    />
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
        const token = loginResponse.data;
        localStorage.setItem("token", token);
        this.token = token;

        // Decode JWT to get userId
        const tokenParts = token.split(".");
        const decodedPayload = JSON.parse(atob(tokenParts[1]));
        const userId = decodedPayload.sub;

        // ✅ Fetch user profile
        const response = await fetch(`http://localhost:8080/api/users/${userId}`, {
          headers: { Authorization: token },
        });

        const userData = await response.json();
        if (response.ok && userData.data) {
          this.user = userData.data;
          localStorage.setItem("user", JSON.stringify(userData.data)); // ✅ Store user details
        } else {
          console.error("Failed to fetch user profile:", userData);
        }
      } catch (error) {
        console.error("Error fetching user profile:", error);
      }
    },

    // ✅ Restore session on refresh
    restoreSession() {
      const storedToken = localStorage.getItem("token");
      const storedUser = localStorage.getItem("user");

      if (storedToken && storedUser) {
        try {
          this.token = storedToken;
          this.user = JSON.parse(storedUser);
          console.log("Session restored:", this.user);
        } catch (error) {
          console.error("Error restoring session:", error);
          this.logout(); // Clear invalid session
        }
      }
    },

    showUserCoupons() {
      this.showCouponsPopup = true;
    },

    async logout() {
      const token = localStorage.getItem("token");
      if (!token) return;

      try {
        const response = await fetch(`http://localhost:8080/api/users/logout`, {
          method: "POST",
          headers: { Authorization: token },
        });

        if (response.ok) {
          localStorage.removeItem("token");
          localStorage.removeItem("user");
          this.user = null;
          this.token = null;
          alert("Logout successful!");
        } else {
          alert("Logout failed!");
        }
      } catch (error) {
        console.error("Error logging out:", error);
      }
    },
  },
};
</script>
