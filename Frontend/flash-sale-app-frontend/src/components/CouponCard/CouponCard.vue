<template>
    <div class="card d-flex flex-row p-3 m-2">
        <img :src="coupon.imageURL" class="img-fluid" style="width: 150px; height: 150px; object-fit: cover;">
        <div class="d-flex flex-column flex-grow-1 ms-3">
            <h5>{{ coupon.name }}</h5>
            <p class="text-muted">{{ coupon.description }}</p>
            <div class="mt-auto d-flex" style="justify-content:space-between;">
                <p><strong>Price:</strong> 💰{{ coupon.price }} | <strong>Qty:</strong> {{ coupon.quantity }}</p>
                <button class="btn btn-primary" style="min-width: 100px;" @click="buyCoupon" :disabled="!user">
                    Buy
                </button>
            </div>
        </div>
    </div>

    <!-- Snackbar Component -->
    <Snackbar ref="snackbar" />
</template>

<script>
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';
import Snackbar from '../Snackbar/Snackbar.vue';

export default {
    components: { Snackbar },
    props: ['coupon', 'user'],
    data() {
        return {
            stompClient: null,
            orderStatus: null
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
                const response = await fetch('http://localhost:8080/api/coupons/buy', {
                    method: 'POST',
                    headers: {
                        'Authorization': token,
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    body: formData.toString()
                });

                console.log(response);
                if (!response.ok) {
                    const errorMessage = await response.text();
                    throw new Error(errorMessage);
                }

                const data = await response.json();
                this.$refs.snackbar.showMessage("Order placed successfully!", "green", 3000);
                this.connectWebSocket(data.data); // ✅ Order ID from response

            } catch (error) {
                console.error('Error purchasing coupon:', error);
                this.$refs.snackbar.showMessage(`Error: ${JSON.parse(error.message).message}`, "red", 3000);
            }
        },

        connectWebSocket(orderId) {
            if (!this.stompClient || !this.stompClient.connected) {
                let socket = new SockJS('http://localhost:8080/ws');
                this.stompClient = Stomp.over(socket);

                this.stompClient.connect({}, () => {
                    this.stompClient.subscribe(`/topic/order-status/${orderId}`, (message) => {
                        this.orderStatus = JSON.parse(message.body);
                        this.$refs.snackbar.showMessage(`Order Update: ${this.orderStatus}`, "blue", 3000);
                    });
                });
            } else {
                this.stompClient.subscribe(`/topic/order-status/${orderId}`, (message) => {
                    this.orderStatus = JSON.parse(message.body);
                    this.$refs.snackbar.showMessage(`Order Update: ${this.orderStatus}`, "blue", 3000);
                });
            }
        }
    }
};
</script>

