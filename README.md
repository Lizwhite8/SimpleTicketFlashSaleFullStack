# **Flash Sale System Requirements Document**

[1. Project Overview](#1-project-overview)

[2. Tech Stack](#2-tech-stack)

[3. Design Overview](#3-design-overview)

[Flash Sale User Qualification Check with Redis and Lua for High-Concurrency Management](#flash-sale-user-qualification-check-with-redis-and-lua-for-high-concurrency-management)

[Asynchronous Order Processing with Kafka, WebSocket, and Redis Pub/Sub](#asynchronous-order-processing-with-kafka-websocket-and-redis-pubsub)

[Multi-Level Caching Strategy (Caffeine + Redis)](#multi-level-caching-strategy-caffeine--redis)

[Cache Breakdown Protection with Redisson Mutual Exclusion](#cache-breakdown-protection-with-redisson-mutual-exclusion)

[Authentication with Keycloak and Spring Security](#authentication-with-keycloak-and-spring-security)

[Load Balancing with NGINX Reverse Proxy](#load-balancing-with-nginx-reverse-proxy)

[Database Choice (MySQL)](#database-choice-mysql)

[Deployment Strategy and Challenges](#deployment-strategy-and-challenges)


## **1. Project Overview**

****Project URL:** [**Vite App**](http://3.145.159.42/)**

![](https://lh7-rt.googleusercontent.com/docsz/AD_4nXfxTMzgHCrGQjYyzm7t-NLz1TsevTYgNTlG4CYfE82WELZOs--Hv-aIYH7TbBSrlldaYkLESZHuJYxOik4IN_cv2AmYkPN-dCwogrtP_vBGCBucmEbrh4hmMUi7iUPGj4ryoCq_?key=gh4idSIF-ZqgJoW6XwLW-efd)

The Flash Sale System is designed to facilitate high-speed coupon purchases during flash sales while ensuring performance, security, and data consistency. Users can register and log in using their email without verification, browse available coupons, and attempt to purchase them in real time. The system does not support password modification or real payment, as the primary focus is on managing high-demand, time-sensitive coupon sales efficiently.

A major challenge in building this system is handling high concurrency while maintaining strong consistency. Flash sales generate massive user requests within a short period, leading to risks such as database overload, overselling of coupons, and race conditions in order processing. To mitigate these challenges, the system relies on a combination of **Redis and Lua scripting** for atomic operations, **Kafka, Redis Pub/Sub, and WebSocket** for asynchronous processing, and **multi-level caching mechanisms** to reduce database queries. The deployment is currently on **AWS EC2**, running in **Docker Containers within a public subnet**, but future plans include migrating to **EKS deployed in a private  subnet for improved scalability, security, and high availability**.


## **2. Tech Stack**

The frontend is built using **Vue.js and Bootstrap**, providing a user-friendly interface. The backend is developed with **Spring Boot**, utilizing **Spring Data JPA** for database interactions and **Spring Security with Keycloak** for authentication. **MySQL** is used as the primary database due to its **ACID compliance**, ensuring transaction integrity during coupon sales.

For caching and high-speed inventory management, the system integrates **Redis** with **Lua scripting** to ensure atomic stock deductions and prevent overselling. A **multi-layer caching strategy** combining **Caffeine for local memory caching and Redis for distributed caching** reduces query load on MySQL. **Kafka, WebSocket, and Redis Pub/Sub** work together to manage asynchronous order processing and updates, ensuring a seamless payment experience.

Deployment is currently on **AWS**, with **S3 for image storage** and **Kubernetes running inside an EC2 instance** within a public subnet. While **EKS was not chosen** due to cost considerations and the project's personal training purpose, a migration is planned to ensure proper **horizontal scaling, high availability, and security**. **NGINX serves as a reverse proxy** to handle frontend hosting and distribute backend requests efficiently.


## **3. Design Overview**

### **Flash Sale User Qualification Check with Redis and Lua for High-Concurrency Management**

![](https://lh7-rt.googleusercontent.com/docsz/AD_4nXc756vXqeV4yBgM35dNmGCFf0mhosGDw7AsN6LKPibMWZfPsngZ3e9YGxjB2VwUWG8Yiyd6pA-B0rwEqe1TrUwMpP6vFf-Oel957zCZSK6uiejsJ8Dn_r9GPnhfayxAvs_fgkuDTg?key=gh4idSIF-ZqgJoW6XwLW-efd)

Managing **high concurrency** while preventing **overselling** presents one of the most significant technical challenges in flash sale systems. To optimize coupon stock management, the system leverages **Redis** to handle inventory counts, eliminating the need for constant database queries and ensuring faster performance. However, since Redis operations are not inherently transactional, **Lua scripting** is introduced to enforce **atomicity**, allowing stock verification and deduction to occur in a single, indivisible step. This strategy effectively prevents **race conditions** without requiring explicit locks, making it ideal for handling high-speed transactions during peak sale events.

To ensure that users are qualified and prevent **duplicate orders**, the system uses a **set-based validation** approach within Redis. Before processing any order, the system checks a Redis set to determine whether the user has previously purchased the same coupon. If the user passes the eligibility check and sufficient stock is available, the system simultaneously deducts inventory and confirms the order within a **single atomic Redis transaction**. This mechanism guarantees that users cannot exceed the allowed purchase limit while maintaining accurate inventory control.

Alternative methods were considered for **stock validation** and deduction but were ultimately dismissed due to performance concerns. Using MySQL transactions was ruled out, as they would create bottlenecks under heavy load, especially during high-traffic flash sales. While **optimistic locking** in MySQL could manage concurrent stock deductions, it introduced additional latency compared to Redis.

Another conventional approach, **pessimistic locking**, involves locking a database row until the transaction is completed to prevent race conditions. However, this method drastically reduces performance in **high-concurrency** scenarios by requiring exclusive access to stock records. This results in increased **contention**, longer wait times, and diminished **throughput**, severely impacting the **user experience** during a flash sale when thousands of users are competing simultaneously.

The final solution—combining **Redis** and **Lua scripting**—proves to be the most efficient method for handling high-speed **stock verification** and deduction without risking **data inconsistencies**. The **atomic** execution of Lua scripts in Redis ensures that stock checks, user qualification, and deductions happen within a single operation. Ultimately, after a successful payment, the final **inventory count** is stored in the **database** for persistent tracking, allowing for accurate reconciliation while minimizing load on the database during peak activity.


### **Asynchronous Order Processing with Kafka, WebSocket, and Redis Pub/Sub**

![](https://lh7-rt.googleusercontent.com/docsz/AD_4nXdbp7hnVpMnGHVQ9lKJwjBBbEAlbhytCOhgVMeJFKfy7XMa5XNfIpEpntDV-_Dm2bMf4OcVvcUbkpPY-CzBOyTv8bKGJt7LB-5l8nMxk83kfPk0ORaMlEkSVnqOvGL2oFZ-cT8h?key=gh4idSIF-ZqgJoW6XwLW-efd)

In handling thousands of concurrent payment requests while ensuring real-time user feedback, the system follows a robust process that combines **Kafka** for event-driven communication, **Redis Pub/Sub** for distributed message delivery, and **WebSocket** for real-time client interaction. This architecture effectively decouples **order placement** from **payment processing**, preventing frontend blocking and ensuring that payment results are delivered to the correct WebSocket connection on the appropriate server instance.

The process begins when a user places an order successfully. Instead of immediately processing the payment synchronously, the system publishes an event to **Kafka** from the coupon service. Kafka’s role is critical here—it serves as a highly reliable, scalable, and durable message queue capable of handling high-throughput event streaming. By decoupling the payment process from the initial order placement, Kafka ensures that the frontend can respond quickly, enhancing the responsiveness of the system under heavy traffic without waiting for payment processing to complete.

Once the payment service consumes the Kafka event, it processes the payment asynchronously. The system extracts relevant details such as the **order ID**, **user ID**, **coupon ID**, and the associated **server ID** from the Kafka message. During payment processing, several outcomes are possible. If the payment is unsuccessful—for instance, due to insufficient user credit—the system initiates a rollback process. This rollback involves restoring the coupon stock in Redis using a **Lua script**, ensuring **atomicity** and preventing race conditions. The Lua script also removes the user from the purchase set associated with the coupon, thereby allowing them to attempt the purchase again if desired. This ensures that the inventory remains accurate and that failed payment attempts don’t result in lost stock.

If the payment is successful, the system deducts the corresponding credit from the user’s account and records the payment details in the **MySQL** database. This includes creating an entry in the `UserCoupon` table to record the successful transaction and reducing the coupon stock in the database within a synchronized block to maintain consistency. Once these operations are complete, the payment service must notify the user of the outcome.

At this point, **Redis Pub/Sub** becomes essential for efficiently routing the payment result to the appropriate server instance. Each server instance maintains a unique server ID generated at startup. After processing the payment, the payment service publishes a message regarding the payment result to a Redis topic named after the server ID. For example, if the WebSocket connection was established on a server with the ID `123e4567-e89b-12d3-a456-426614174000`, the payment result message will be sent to the `payment_update:123e4567-e89b-12d3-a456-426614174000` channel.

This design ensures that only the relevant server receives the update, minimizing unnecessary message handling by other server instances. Once the appropriate server receives the message through Redis Pub/Sub, it uses the stored WebSocket session information to push the payment result directly to the connected client in real time. This ensures that users receive immediate feedback on their transaction status.

The choice of **Kafka** and **Redis Pub/Sub** is driven by their distinct strengths. Kafka offers durability, scalability, and high throughput, making it ideal for processing large volumes of payment requests and ensuring that no payment events are lost during transmission. In contrast, Redis Pub/Sub is chosen for its ability to deliver low-latency, real-time messages across distributed server instances. The trade-off with Redis Pub/Sub, however, is that it does not guarantee message delivery—if a server is down or experiences network issues when a message is published, that message will be lost. Nevertheless, this trade-off is acceptable in this system because WebSocket updates are primarily for real-time user notifications and do not affect the integrity of the order data stored in the database. Users can always refresh their page or request an updated order status through an API if a notification is missed.

In summary, the system effectively integrates Kafka, Redis Pub/Sub, and WebSocket to handle high-concurrency payment processing with real-time feedback. Kafka reliably manages asynchronous event streaming and decouples frontend order placement from backend processing. Redis Pub/Sub ensures efficient message delivery to the appropriate server instance for real-time WebSocket communication. The rollback process ensures that failed payments do not affect stock integrity, while successful transactions update both the user’s account and the order database. The architecture prioritizes scalability, real-time responsiveness, and consistency, ensuring that the system remains robust under heavy traffic while providing a seamless user experience.


### **Multi-Level Caching Strategy (Caffeine + Redis)**

![](https://lh7-rt.googleusercontent.com/docsz/AD_4nXeRcuALagotk1O_gNU4Os_ZgZRyZsYzOKUUYL6L43-sIQq7liLww0vnzxmjga83Jx24ZmEt23ErT3LJKsHgOrnhfWpIySWf4tSlpGkNjTuftXbOygmbIXE7138gxwg9Tb2eY5F2fw?key=gh4idSIF-ZqgJoW6XwLW-efd)

Frequent access to coupon descriptions creates significant database load, especially during peak times when thousands of users browse available coupons simultaneously. To optimize performance and reduce unnecessary database queries, the system employs a **multi-level caching strategy** that leverages both **Caffeine cache for ultra-fast in-memory access** and **Redis for distributed caching across multiple instances**.

The **Caffeine cache** is stored locally within each application instance, providing **millisecond-level response times** for frequently accessed data. This cache expires every **30 seconds**, ensuring that recently queried coupons remain accessible without requiring a request to Redis or the database. However, since Caffeine is **local to each server**, it does not help with data consistency across different application instances. To solve this, **Redis is used as a shared distributed cache**, allowing all instances to access and update the same coupon data. The Redis cache is configured to expire every **2 minutes**, reducing the frequency of direct database queries while keeping the data reasonably fresh.

Within Redis, two distinct types of caches are maintained: **the pagination result cache** and the **coupon quantity cache**. The **pagination result cache** is structured as a **list**, storing paginated coupon details such as name, description, price, and image URLs. This cache enables fast retrieval for users browsing available coupons, minimizing the number of MySQL queries required for listing operations. Separately, the **coupon quantity cache** is stored as a **string**, representing the real-time inventory count for each coupon. The quantity cache is **immediately updated** whenever a purchase occurs, ensuring that all subsequent transactions reflect the latest stock availability.

A key trade-off in this caching approach is the **potential inconsistency between the pagination result cache and the real-time inventory count**. Since the pagination cache is updated only every **2 minutes**, it is possible that a user may see a coupon with available stock on the frontend, but when they attempt to purchase it, the real-time quantity cache in Redis reflects that the stock has already been depleted. This discrepancy occurs because the pagination list is cached separately from the inventory count and is not updated after every purchase.

One alternative approach to eliminate this inconsistency would be to query the **real-time quantity cache** for each coupon in the pagination result before returning the data to the frontend. This would ensure that the displayed stock levels are always up to date. However, this approach would significantly degrade performance, as each paginated request would require **multiple additional Redis queries**—one for each coupon listed. Under high traffic, these additional queries could lead to **increased response times and unnecessary Redis load**, ultimately negating the benefits of caching.

Given that the most **critical aspect of the system is the purchasing operation**, not the browsing experience, the decision was made to **accept a 2-minute delay in the inventory update within the pagination cache**. Users may occasionally see outdated inventory counts in the UI, but the actual purchase operation is always validated against the **real-time quantity cache**, preventing any overselling or incorrect orders. This trade-off prioritizes **scalability and efficiency** while maintaining **strong consistency where it matters most—during transactions**.

By combining **Caffeine for ultra-fast local caching, Redis for distributed shared caching, and a well-defined separation of pagination and inventory caches**, the system ensures that coupon browsing remains smooth and responsive while keeping inventory tracking accurate and efficient. This approach strikes a balance between **performance, consistency, and scalability**, ensuring that the system can handle high traffic loads without unnecessary strain on the database.


### **Cache Breakdown Protection with Redisson Mutual Exclusion**

![](https://lh7-rt.googleusercontent.com/docsz/AD_4nXeK3swcHnftgjMHFo8EHuRgmWSBkKCRQZwCWxK-fEFXqSthW-g5xL7knJt4HD32IMQaHSgL3AlJpbWYOSclEos80VL2mnhehlNFf1QBWiX2F4M9Om5W8SZToQhMlMTi_yFNL617?key=gh4idSIF-ZqgJoW6XwLW-efd)

In this system, **cache breakdown** can occur when a highly requested coupon—often referred to as a **hotkey**—expires from **Redis** and is not immediately repopulated. During high-traffic periods, this presents a significant risk: if multiple processes attempt to access the same expired coupon simultaneously, they will all bypass the cache and query **MySQL** directly. This sudden surge of requests can overwhelm the database, causing severe performance degradation or, in extreme cases, system crashes.

To illustrate this issue, imagine a scenario where **Process 1** and **Process 2** both try to retrieve the same coupon from Redis. If the coupon key has already expired, neither process will find the cached data. Without any form of coordination, both processes will proceed to query MySQL simultaneously. If hundreds or even thousands of users are trying to access this coupon at once—especially during peak sales events—the database could be flooded with simultaneous queries for the same data, potentially leading to system failure due to the sheer volume of requests.

![](https://lh7-rt.googleusercontent.com/docsz/AD_4nXdgkdGF2UsGycFmz4j0crDI6moh5yRF3X6QGCKrWsmS6d4VL4dnvLz81KxdVnkRCzvGMyEpjc_jw34nbhfQmDUgVYSx2ay3YoY5aRDohr67aYkMPID3sSRixFzAqi9nkizxppQwzA?key=gh4idSIF-ZqgJoW6XwLW-efd)

To prevent this, the system employs a **mutual exclusion lock** using **Redisson's distributed locking mechanism**. When Process 1 detects that the coupon is missing from Redis, it attempts to acquire a Redis-based lock. If successful, it becomes the only process permitted to query MySQL for that coupon. Once the data is retrieved, Process 1 repopulates the Redis cache with the updated coupon information and then releases the lock. Meanwhile, when Process 2 tries to acquire the same lock, it finds that the lock is already held by Process 1. Instead of waiting indefinitely or attempting its own database query, Process 2 immediately returns a **"Try Again Later"** response to the client. This trade-off is acceptable because cache reconstruction typically completes within milliseconds, and cache breakdown events are infrequent.

However, mutual exclusion alone does not fully resolve the issue—especially in scenarios where the process holding the lock crashes or encounters an unexpected error. If Process 1 crashes after acquiring the lock but before releasing it, the lock could remain indefinitely held. This would block all subsequent processes from updating the cache, potentially leading to stale data or unavailable services.

This is where **Redisson** proves invaluable. It offers an auto-expiring distributed lock with built-in protection through its **Watchdog mechanism**. The Redisson Watchdog automatically extends the lock’s expiration as long as the process holding the lock remains active and functioning. This means that during normal operation, the lock will not expire prematurely, ensuring that cache rebuilding is completed without interference. However, if the process crashes or fails unexpectedly, the lock will automatically expire after a set timeout. This feature prevents other processes from being permanently blocked and allows them to acquire the lock and continue with cache reconstruction when necessary.

The choice of **Redisson** is driven by its ability to provide both **distributed mutual exclusion** and a **fail-safe Watchdog mechanism**. This combination ensures that only one process can repopulate the cache at a time, effectively preventing cache breakdown. At the same time, it safeguards against potential deadlocks caused by unexpected process failures. By integrating Redisson’s distributed locking and Watchdog features, the system protects **MySQL** from sudden load spikes, maintains consistent cache integrity, and ensures stable performance under heavy traffic—allowing the platform to handle high-concurrency scenarios reliably without compromising database stability.


### **Authentication with Keycloak and Spring Security **![](https://lh7-rt.googleusercontent.com/docsz/AD_4nXfx5vIJdAcv24o93OP8ao0ngPd795yXrt6B68dUi_HTLcUUSAHDQRnX7Mf7Pr0751AZPVNXNi24P-F7So0X_K_GlBt6F0Yl8S0_iJR8h3ELEoojaDtrmg4BN2fhcgT1jyBvN5C1IQ?key=gh4idSIF-ZqgJoW6XwLW-efd)****

User authentication and API security are **critical components** of the system, ensuring that only authorized users can access restricted resources while maintaining **scalability, flexibility, and ease of management**. The system implements **Keycloak**, integrated with **Spring Security**, as the central authentication and authorization provider. **JWT (JSON Web Token) tokens** are used to enable **stateless authentication**, allowing secure user identity verification across multiple microservices without requiring persistent session storage.

The primary reason for choosing **Keycloak** is to **decouple authentication from business logic**, ensuring that the backend application focuses exclusively on **core functionalities such as coupon management and order processing**. By outsourcing authentication to Keycloak, the system benefits from a **robust access management solution** that supports **OAuth2** out of the box. This separation also simplifies user management, as administrators can configure authentication policies, roles, and permissions via Keycloak’s built-in administration console without modifying backend code.

The authentication flow begins when a user logs in using their **email and password**. The request is forwarded to **Keycloak**, which validates the credentials and issues a **JWT access token**. This token contains the user’s identity information, roles, and expiration details, allowing it to be verified by any backend service without additional database queries. Once authenticated, the frontend includes the JWT in every subsequent request’s **Authorization header**, which the backend verifies before processing any API calls.

An alternative approach to authentication was to implement a **custom JWT-based authentication system** directly within the backend. While feasible, this would require additional effort to manage **token issuance, refresh mechanisms, encryption, revocation, and user session tracking**. A self-managed authentication system also increases **maintenance overhead**, particularly when dealing with security vulnerabilities, token expiration policies, and user role management. By contrast, **Keycloak provides a battle-tested, production-ready solution**, reducing security risks and development complexity.

By implementing **Keycloak with Spring Security**, the system ensures that authentication is **scalable, centralized, and easy to manage**, while **JWT tokens provide a lightweight, stateless authentication mechanism** that enhances API security. This approach not only simplifies backend development but also allows for **seamless integration with future identity providers and enterprise authentication solutions**, ensuring the system remains **extensible and secure** in the long term.


### **Load Balancing with NGINX Reverse Proxy**

![](https://lh7-rt.googleusercontent.com/docsz/AD_4nXffPMJb_qt20dP6Hgbmatdz53OiHQzKXesjUZIY63i_TChOG1AHLeS-JtZFj1bnNBfrI-CFjuPAy0dUkg9xjRyG7GGA_T2jt-0_8BAkHWqVPAVHKLbyjc9puVj8oi5BQs2y_re4Og?key=gh4idSIF-ZqgJoW6XwLW-efd)

To efficiently handle user requests, **NGINX is used as a reverse proxy**, acting as an intermediary between clients and backend services. It plays a crucial role in **hosting the frontend, distributing traffic across backend servers, improving performance, and enhancing security**. Without a reverse proxy, all user requests would be sent directly to the backend servers, increasing resource consumption, slowing response times, and creating bottlenecks under high traffic conditions. By integrating NGINX, the system benefits from **load balancing, request routing, caching, compression, and security enhancements**, ensuring a smooth and efficient user experience.

One of the primary advantages of using a reverse proxy is **load distribution**. Instead of overwhelming a single backend instance, NGINX can **evenly distribute incoming requests across multiple backend servers**, preventing any one server from being overloaded. This is particularly important in high-concurrency scenarios, such as **flash sales**, where thousands of users may simultaneously attempt to purchase coupons. Additionally, NGINX **reduces latency** by caching static assets and compressing responses, allowing users to experience faster page loads and seamless interactions.

Beyond performance improvements, NGINX also **enhances security** by acting as a shield between the external internet and internal backend services. By terminating SSL/TLS connections, it offloads **HTTPS encryption and decryption**, reducing the computational burden on backend servers. Additionally, it helps **mitigate DDoS attacks, prevent direct access to backend APIs, and enforce rate limiting** to filter out excessive requests from malicious or misbehaving clients.

An alternative web server considered for this role was **Apache HTTP Server**, a widely used open-source web server. While Apache is powerful and flexible, **NGINX is better suited for handling high-concurrency workloads** due to its **event-driven, asynchronous architecture**. Apache uses a **thread-based model**, which creates a new process or thread for each request, consuming more memory and struggling under heavy traffic. In contrast, **NGINX is designed to handle thousands of concurrent connections efficiently** by using a **non-blocking, event-driven model** that processes multiple requests in a single worker thread.

NGINX also offers **superior performance in serving static content**, making it ideal for hosting the frontend. It is optimized for **reverse proxying, load balancing, and handling massive concurrent connections**, whereas Apache excels in **dynamic content processing** through modules like PHP, which is unnecessary for this system. Given the need for **high performance, efficient load balancing, and low memory usage**, **NGINX was chosen as the preferred reverse proxy over Apache**, ensuring a scalable and resilient infrastructure for the flash sale platform.


### **Database Choice (MySQL)**

In this flash sale system, **MySQL** is chosen primarily for its strong adherence to **ACID (Atomicity, Consistency, Isolation, Durability)** properties and its **rigid schema design**, which ensures strict transactional consistency and data integrity. These features are essential in scenarios where multiple users are simultaneously attempting to purchase the same coupons. MySQL’s transactional guarantees prevent **race conditions**, maintain **accurate stock management**, and ensure that critical financial operations are handled reliably.

The need for strict consistency is particularly important in a high-concurrency environment where overselling could lead to significant financial and reputational risks. MySQL’s ability to handle structured data with a defined schema ensures that every transaction is validated against predefined rules, maintaining data consistency even under heavy load.

Alternatives such as **MongoDB** offer advantages in terms of **flexible schema design** and **horizontal scalability**, making them attractive for systems that require rapid development or handle unstructured data. However, MongoDB follows an **eventual consistency** model, meaning that under high-concurrency conditions, there is a risk that users could see outdated data, potentially leading to issues such as overselling. In a flash sale system where real-time accuracy of inventory is non-negotiable, this trade-off makes MongoDB less suitable.

Given the system’s need for **strict transactional control**, **accurate inventory tracking**, and **schema rigidity**, MySQL remains the most appropriate choice. Its robust consistency model ensures that all transactions are reliably processed, safeguarding the integrity of both financial data and stock management during periods of high traffic.


### **Deployment Strategy and Challenges**

![](https://lh7-rt.googleusercontent.com/docsz/AD_4nXccwb5iex1c-VfRQS2J_9bAnyfvgrar3n2-0fAlPg99h3sXr8eVvC5FkGFCeEn6LVOvRu-itEMfmhZdLKFSrM2grOPAazWxVS4Uqw6wl2jYSOnTVnf-vxepVI-6IaUQ5K_tcLJrfA?key=gh4idSIF-ZqgJoW6XwLW-efd)

The current development environment hosts all middleware and backend services using **Docker containers** on **AWS EC2** instances, deployed within a **public subnet**. This setup facilitates a streamlined development process by enabling direct external access without the need for additional networking configurations like a NAT gateway. It offers a **cost-effective** solution for development purposes while providing hands-on experience in managing containerized applications manually. Additionally, **AWS S3** is utilized for storing **coupon images**, eliminating the need for local storage and allowing seamless media asset access across different services.

However, this configuration presents several critical limitations, especially in terms of **scalability**, **availability**, and **security**. The current deployment lacks **horizontal scaling** and **high availability** since all workloads run within a **single EC2 instance**. This means the system cannot dynamically scale with increased traffic, leading to potential **performance degradation** when resource limits are reached. Furthermore, if the EC2 instance fails, the entire application becomes unavailable, causing **downtime** and loss of service continuity.

Security is another major concern. Running backend services in a **public subnet** exposes them directly to the internet, increasing the risk of **unauthorized access**, **DDoS attacks**, and **data breaches**. Ideally, sensitive services should be hosted in a **private subnet**, with access restricted through an **Elastic Load Balancer (ELB)** or **API Gateway**. While configuring a private subnet would enhance security, it currently adds complexity and cost due to the need for a **NAT gateway** or **VPC peering**.

To address these issues, there is a future plan to **migrate to AWS Elastic Kubernetes Service (EKS)** within a **private subnet**. EKS offers a fully managed Kubernetes environment, handling critical operations such as **control plane management**, **scalability**, and **security**. In this upgraded architecture, workloads will be distributed across **multiple availability zones**, ensuring **fault tolerance** and enabling **auto-scaling** based on resource demand.

Access to the private subnet will be secured using a **Bastion Host**, which will allow controlled, secure administrative access without exposing the infrastructure to the public internet. This setup will significantly enhance security while maintaining operational flexibility. Incoming traffic will be securely routed to backend services through an **Elastic Load Balancer (ELB)**, ensuring only necessary endpoints are exposed.

While the current Docker-based EC2 deployment serves as a practical and cost-effective solution for **development and testing**, it is not viable for production due to inherent limitations in **scalability**, **availability**, and **security**. Migrating to **AWS EKS** with private networking and a bastion host will provide a robust, scalable, and secure infrastructure capable of handling dynamic workloads and ensuring system resilience.
