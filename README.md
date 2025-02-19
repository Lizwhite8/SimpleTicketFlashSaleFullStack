# **Flash Sale System Requirements Document**

## **1. Project Overview**

The Flash Sale System is designed to facilitate high-speed coupon purchases during flash sales while ensuring performance, security, and data consistency. Users can register and log in using their email without verification, browse available coupons, and attempt to purchase them in real time. The system does not support password modification or payment processing, as the primary focus is on managing high-demand, time-sensitive coupon sales efficiently.

A major challenge in building this system is handling high concurrency while maintaining strong consistency. Flash sales generate massive user requests within a short period, leading to risks such as database overload, overselling of coupons, and race conditions in order processing. To mitigate these challenges, the system relies on a combination of **Redis and Lua scripting** for atomic operations, **Kafka and WebSocket** for asynchronous processing, and **multi-level caching mechanisms** to reduce database queries. The deployment is currently on **AWS EC2**, running **Kubernetes within a public subnet**, but future plans include migrating to **EKS for improved scalability and high availability**.

***


## **2. Tech Stack**

The frontend is built using **Vue.js and Bootstrap**, providing a user-friendly interface. The backend is developed with **Spring Boot**, utilizing **Spring Data JPA** for database interactions and **Spring Security with Keycloak** for authentication. **MySQL** is used as the primary database due to its **ACID compliance**, ensuring transaction integrity during coupon sales.

For caching and high-speed inventory management, the system integrates **Redis** with **Lua scripting** to ensure atomic stock deductions and prevent overselling. A **multi-layer caching strategy** combining **Caffeine for local memory caching and Redis for distributed caching** reduces query load on MySQL. **Kafka, WebSocket, and Redis Pub/Sub** work together to manage asynchronous order processing and updates, ensuring a seamless payment experience.

Deployment is currently on **AWS**, with **S3 for image storage** and **Kubernetes running inside an EC2 instance** within a public subnet. While **EKS was not chosen** due to cost considerations and the project's personal training purpose, a migration is planned to ensure proper **horizontal scaling, high availability, and security**. **NGINX serves as a reverse proxy** to handle frontend hosting and distribute backend requests efficiently.

***


## **3. Design Overview**

### **Flash Sale Execution with Redis and Lua**

Managing high concurrency while preventing overselling is one of the biggest technical challenges in flash sale systems. The system optimizes coupon stock management by keeping inventory counts in **Redis**, eliminating the need for frequent database queries. Since Redis operations are inherently fast but not transactional, **Lua scripting is used** to enforce atomicity, ensuring that stock verification occurs in a single step. This approach prevents race conditions without requiring explicit locks, making it ideal for high-speed transactions.

To further prevent duplicate orders, the system employs a **set-based validation approach in Redis**. Before processing an order, a **Redis set** checks whether the user has already purchased the same coupon. If the user is eligible and stock is available, the system deducts stock and processes the order in a **single atomic Redis transaction**. This guarantees that a user cannot purchase the same coupon more than the allowed limit while also ensuring accurate inventory control.

Several alternative approaches were considered for handling stock validation and deduction. **MySQL transactions** were a potential solution but were ruled out due to **performance bottlenecks under heavy load**, especially during peak flash sale events. Another option was **optimistic locking in MySQL**, which can handle concurrent stock deductions but still introduces higher latency compared to Redis.

One more traditional approach is **pessimistic locking**, where a database row is locked until the transaction is completed. While effective in preventing race conditions, **pessimistic locking is not recommended** due to its **slow performance under high concurrency**. Every transaction would require exclusive access to the stock record, leading to increased contention, longer wait times, and reduced throughput. This approach would severely impact user experience during a flash sale, where thousands of users attempt to purchase simultaneously.

Ultimately, the chosen approach using **Redis and Lua scripting** is the fastest and most efficient way to handle **high-speed stock verification and deductions** without risking data inconsistencies. The atomic nature of Lua scripts in Redis ensures that stock checks and deductions happen within the same operation, preventing race conditions and significantly reducing the load on the database.


### **Asynchronous Order Processing with Kafka, WebSocket, and Redis Pub/Sub**

Processing thousands of concurrent payment requests while ensuring real-time user feedback presents two key challenges: **decoupling order placement from payment processing to prevent blocking** and **delivering payment results to the correct WebSocket connection on the right server instance**. The system tackles these challenges using **Kafka for event streaming, WebSocket for real-time communication, and Redis Pub/Sub for distributed message delivery**.

When a user successfully places an order, the coupon service **publishes an event to Kafka**, which is then consumed by the payment service. This approach ensures that the frontend does not need to wait for the entire payment process to complete before responding to the user, improving responsiveness during peak traffic. Within the payment service, **a thread pool distributes tasks efficiently**, allowing multiple payments to be processed in parallel. Once the payment is completed, the system must **push the status update to the correct WebSocket connection** for the user who initiated the transaction.

Since WebSockets are **stateful** and tied to a specific server instance, the payment service must ensure that the **payment result message is routed to the exact server where the user’s WebSocket connection was initially established**. To achieve this, the system uses **Redis Pub/Sub**, where each server subscribes to payment result notifications and relays them to the appropriate WebSocket connections.

Each server instance is assigned a unique **server ID** in the form of a **UUID** at startup. When a user establishes a WebSocket connection, the server that handles the connection **stores the user's session information in Redis**, mapping the user ID to the corresponding **server ID**. This allows any service needing to push updates to look up where the user’s WebSocket is connected.

When a payment transaction completes, the payment service **publishes a payment result message to a Redis topic named after the user’s server ID**. The naming convention follows a pattern such as `payment_update:<server_id>`. For example, if the user’s WebSocket connection was established on a server with the UUID `123e4567-e89b-12d3-a456-426614174000`, the payment service would publish the message to the Redis channel `payment_update:123e4567-e89b-12d3-a456-426614174000`. This ensures that only the relevant server receives and processes the update, preventing unnecessary message handling by other instances.

While Redis Pub/Sub is a lightweight and efficient messaging system, it **does not guarantee message delivery**. If a subscriber server is down or experiences network issues when a message is published, that message will be lost. However, since **WebSocket updates are primarily for real-time user notifications and do not affect the actual order state in the database**, this potential data loss is acceptable. Even if a WebSocket update is missed, the user can still see their updated order status by refreshing the page or making an API request to the backend.

By combining Kafka for **event-driven payment processing**, WebSocket for **instant user feedback**, and Redis Pub/Sub for **efficient routing of real-time updates**, the system ensures that users receive near-instant status updates while maintaining a scalable and robust payment pipeline. The decision to tolerate potential message loss in Redis Pub/Sub simplifies implementation while keeping the core order-processing logic intact within the database.


### **Multi-Level Caching Strategy (Caffeine + Redis)**

Frequent access to coupon descriptions creates significant database load, especially during peak times when thousands of users browse available coupons simultaneously. To optimize performance and reduce unnecessary database queries, the system employs a **multi-level caching strategy** that leverages both **Caffeine cache for ultra-fast in-memory access** and **Redis for distributed caching across multiple instances**.

The **Caffeine cache** is stored locally within each application instance, providing **millisecond-level response times** for frequently accessed data. This cache expires every **30 seconds**, ensuring that recently queried coupons remain accessible without requiring a request to Redis or the database. However, since Caffeine is **local to each server**, it does not help with data consistency across different application instances. To solve this, **Redis is used as a shared distributed cache**, allowing all instances to access and update the same coupon data. The Redis cache is configured to expire every **2 minutes**, reducing the frequency of direct database queries while keeping the data reasonably fresh.

Within Redis, two distinct types of caches are maintained: **the pagination result cache** and the **coupon quantity cache**. The **pagination result cache** is structured as a **list**, storing paginated coupon details such as name, description, price, and image URLs. This cache enables fast retrieval for users browsing available coupons, minimizing the number of MySQL queries required for listing operations. Separately, the **coupon quantity cache** is stored as a **string**, representing the real-time inventory count for each coupon. The quantity cache is **immediately updated** whenever a purchase occurs, ensuring that all subsequent transactions reflect the latest stock availability.

A key trade-off in this caching approach is the **potential inconsistency between the pagination result cache and the real-time inventory count**. Since the pagination cache is updated only every **2 minutes**, it is possible that a user may see a coupon with available stock on the frontend, but when they attempt to purchase it, the real-time quantity cache in Redis reflects that the stock has already been depleted. This discrepancy occurs because the pagination list is cached separately from the inventory count and is not updated after every purchase.

One alternative approach to eliminate this inconsistency would be to query the **real-time quantity cache** for each coupon in the pagination result before returning the data to the frontend. This would ensure that the displayed stock levels are always up to date. However, this approach would significantly degrade performance, as each paginated request would require **multiple additional Redis queries**—one for each coupon listed. Under high traffic, these additional queries could lead to **increased response times and unnecessary Redis load**, ultimately negating the benefits of caching.

Given that the most **critical aspect of the system is the purchasing operation**, not the browsing experience, the decision was made to **accept a 2-minute delay in the inventory update within the pagination cache**. Users may occasionally see outdated inventory counts in the UI, but the actual purchase operation is always validated against the **real-time quantity cache**, preventing any overselling or incorrect orders. This trade-off prioritizes **scalability and efficiency** while maintaining **strong consistency where it matters most—during transactions**.

By combining **Caffeine for ultra-fast local caching, Redis for distributed shared caching, and a well-defined separation of pagination and inventory caches**, the system ensures that coupon browsing remains smooth and responsive while keeping inventory tracking accurate and efficient. This approach strikes a balance between **performance, consistency, and scalability**, ensuring that the system can handle high traffic loads without unnecessary strain on the database.


### **Cache Breakdown and Cache Penetration Protection with Redisson Watchdog and null caching**

When a coupon is not found in **Redis**, multiple processes may simultaneously query **MySQL** for the missing data. Under high traffic conditions, this can lead to a situation where **hundreds or even thousands of requests hit the database simultaneously**, overwhelming MySQL and potentially causing a crash. This issue, known as **cache breakdown**, occurs when a hotkey (a frequently accessed coupon) expires from Redis and is not immediately repopulated. To prevent this, the system employs a **Redisson Watchdog mechanism with distributed locking**, ensuring that only **one process at a time** retrieves the missing coupon information from MySQL and updates Redis, while others wait or return an error.

Consider a scenario where **Process 1** and **Process 2** both attempt to retrieve the same coupon from Redis, but the coupon key has expired. Without any locking mechanism, both processes would **miss the cache** and proceed to query the database, leading to a potential **database overload** if many users request the same coupon at once.

With the **Redisson Watchdog mechanism**, mutual exclusion is enforced through **a distributed lock**. When **Process 1** detects that the coupon is not in Redis, it attempts to acquire a **Redis-based lock**. If successful, it queries MySQL for the coupon details, repopulates Redis with the latest data, and then releases the lock. Meanwhile, **Process 2** also attempts to acquire the lock, but since **Process 1 already holds it**, Process 2 fails to obtain the lock. Instead of waiting indefinitely or proceeding with a database query, **Process 2 immediately returns a "Server Busy" response to the client**. This is an **acceptable trade-off**, as cache reconstruction usually takes only a few milliseconds, and such cache misses **do not occur frequently**.

**Redisson was chosen** because it provides a **distributed, auto-expiring lock**, ensuring that the lock does not remain indefinitely if a process crashes or encounters an error. Without this, if **Process 1 crashes before releasing the lock**, **Process 2 and all subsequent processes would be permanently blocked** from repopulating the cache. The **Radisson Watchdog** solves this issue by automatically extending the lock **as long as the process holding it is still active**, preventing premature expiration while ensuring **lock safety in the event of failure**.

Beyond **cache breakdown**, another critical issue is **cache penetration**, which occurs when users repeatedly request a **non-existent coupon**. In a traditional caching approach, every request for a missing coupon **bypasses Redis and queries MySQL**, leading to **unnecessary database load**. To mitigate this, the system employs **null caching**, where **a null value is temporarily stored in Redis** when MySQL confirms that the coupon does not exist. This ensures that subsequent requests for the same non-existent coupon are served directly from Redis, **avoiding repeated database hits**.

An alternative approach to **cache penetration prevention** is using a **Bloom Filter**, a probabilistic data structure that checks whether a key exists before querying the database. While **Bloom Filters are effective in preventing unnecessary queries**, they come with **two key drawbacks**. First, **they increase system complexity**, requiring additional memory management and lookup logic. Second, **they are not 100% accurate**, as they introduce **false positives**, meaning some non-existent coupons might still be incorrectly assumed to exist, causing occasional database queries. Given these trade-offs, **null caching was chosen** as the simplest and most effective solution to **prevent cache penetration** while minimizing system complexity.

By combining **Redisson Watchdog for cache breakdown protection** and **null caching for cache penetration prevention**, the system ensures **stable database performance**, avoiding **sudden load spikes** while maintaining **efficient cache utilization**.


### **Authentication & Authorization with Keycloak and Spring Security**

User authentication and API security are **critical components** of the system, ensuring that only authorized users can access restricted resources while maintaining **scalability, flexibility, and ease of management**. The system implements **Keycloak**, integrated with **Spring Security**, as the central authentication and authorization provider. **JWT (JSON Web Token) tokens** are used to enable **stateless authentication**, allowing secure user identity verification across multiple microservices without requiring persistent session storage.

The primary reason for choosing **Keycloak** is to **decouple authentication from business logic**, ensuring that the backend application focuses exclusively on **core functionalities such as coupon management and order processing**. By outsourcing authentication to Keycloak, the system benefits from a **robust access management solution** that supports **OAuth2** out of the box. This separation also simplifies user management, as administrators can configure authentication policies, roles, and permissions via Keycloak’s built-in administration console without modifying backend code.

The authentication flow begins when a user logs in using their **email and password**. The request is forwarded to **Keycloak**, which validates the credentials and issues a **JWT access token**. This token contains the user’s identity information, roles, and expiration details, allowing it to be verified by any backend service without additional database queries. Once authenticated, the frontend includes the JWT in every subsequent request’s **Authorization header**, which the backend verifies before processing any API calls.

An alternative approach to authentication was to implement a **custom JWT-based authentication system** directly within the backend. While feasible, this would require additional effort to manage **token issuance, refresh mechanisms, encryption, revocation, and user session tracking**. A self-managed authentication system also increases **maintenance overhead**, particularly when dealing with security vulnerabilities, token expiration policies, and user role management. By contrast, **Keycloak provides a battle-tested, production-ready solution**, reducing security risks and development complexity.

By implementing **Keycloak with Spring Security**, the system ensures that authentication is **scalable, centralized, and easy to manage**, while **JWT tokens provide a lightweight, stateless authentication mechanism** that enhances API security. This approach not only simplifies backend development but also allows for **seamless integration with future identity providers and enterprise authentication solutions**, ensuring the system remains **extensible and secure** in the long term.


### **Load Balancing with NGINX Reverse Proxy**

To efficiently handle user requests, **NGINX is used as a reverse proxy**, acting as an intermediary between clients and backend services. It plays a crucial role in **hosting the frontend, distributing traffic across backend servers, improving performance, and enhancing security**. Without a reverse proxy, all user requests would be sent directly to the backend servers, increasing resource consumption, slowing response times, and creating bottlenecks under high traffic conditions. By integrating NGINX, the system benefits from **load balancing, request routing, caching, compression, and security enhancements**, ensuring a smooth and efficient user experience.

One of the primary advantages of using a reverse proxy is **load distribution**. Instead of overwhelming a single backend instance, NGINX can **evenly distribute incoming requests across multiple backend servers**, preventing any one server from being overloaded. This is particularly important in high-concurrency scenarios, such as **flash sales**, where thousands of users may simultaneously attempt to purchase coupons. Additionally, NGINX **reduces latency** by caching static assets and compressing responses, allowing users to experience faster page loads and seamless interactions.

Beyond performance improvements, NGINX also **enhances security** by acting as a shield between the external internet and internal backend services. By terminating SSL/TLS connections, it offloads **HTTPS encryption and decryption**, reducing the computational burden on backend servers. Additionally, it helps **mitigate DDoS attacks, prevent direct access to backend APIs, and enforce rate limiting** to filter out excessive requests from malicious or misbehaving clients.

An alternative web server considered for this role was **Apache HTTP Server**, a widely used open-source web server. While Apache is powerful and flexible, **NGINX is better suited for handling high-concurrency workloads** due to its **event-driven, asynchronous architecture**. Apache uses a **thread-based model**, which creates a new process or thread for each request, consuming more memory and struggling under heavy traffic. In contrast, **NGINX is designed to handle thousands of concurrent connections efficiently** by using a **non-blocking, event-driven model** that processes multiple requests in a single worker thread.

NGINX also offers **superior performance in serving static content**, making it ideal for hosting the frontend. It is optimized for **reverse proxying, load balancing, and handling massive concurrent connections**, whereas Apache excels in **dynamic content processing** through modules like PHP, which is unnecessary for this system. Given the need for **high performance, efficient load balancing, and low memory usage**, **NGINX was chosen as the preferred reverse proxy over Apache**, ensuring a scalable and resilient infrastructure for the flash sale platform.


### **Database Design & Optimization (MySQL)**

As an **ACID-compliant** database, **MySQL ensures strong transactional consistency**, making it an ideal choice for handling **critical operations such as coupon purchases**. In a flash sale system, where multiple users attempt to buy the same coupons simultaneously, maintaining **data integrity, preventing race conditions, and ensuring accurate stock management** is essential. MySQL's **transactional capabilities and indexing strategies** allow the system to handle high-concurrency workloads efficiently while keeping queries performant.

One of the key **query patterns** in the flash sale platform is **coupon retrieval for browsing**, which is displayed using **pagination**. Given the potential for large datasets, pagination queries need to be optimized to avoid performance bottlenecks. The system uses **the LIMIT clause** to efficiently fetch a subset of coupons, ensuring a smooth browsing experience for users. However, poorly designed pagination queries can lead to **slow query execution times**, especially when dealing with a high number of records.

To improve pagination performance, **a covering index is created on the** `is_deleted` **and** `id` **columns**. This approach allows the query to efficiently filter out **soft-deleted coupons (**`is_deleted = true`**)** and retrieve only active ones (`is_deleted = false`). The **composite index (**`is_deleted`**,** `id`**)** ensures that the database engine can quickly locate and return relevant records **without needing to perform a full table scan**.

The query execution plan can be analyzed using the **EXPLAIN statement**, which provides insights into how MySQL processes a query. By running `EXPLAIN SELECT * FROM coupons WHERE is_deleted = false ORDER BY id LIMIT 10;`, the execution plan reveals whether **the covering index is effectively used**. The goal is to ensure that MySQL uses **an indexed range scan rather than a full table scan**, significantly reducing execution time. If the index is properly utilized, the `EXPLAIN` output should show that **the query uses the index for both filtering (**`is_deleted = false`**) and sorting (**`ORDER BY id`**)**, avoiding unnecessary disk reads.

Additionally, **MySQL’s slow query log** is enabled to monitor and identify **long-running queries** that exceed a predefined execution threshold. By setting `long_query_time = 1`, queries taking longer than 1 second are logged, allowing developers to detect and optimize inefficient queries. If pagination queries appear frequently in the slow query log, further optimizations such as **index tuning, query restructuring, or caching strategies** can be applied.

An alternative to **MySQL** was to use **NoSQL databases such as MongoDB or DynamoDB**, which offer flexible schema design and scalability advantages. However, these databases **lack strong consistency guarantees**, which are critical in a **financially sensitive system where inventory accuracy is paramount**. NoSQL databases are **eventually consistent**, meaning that under high concurrency, users might see outdated inventory levels, leading to potential overselling issues. Given the need for **strict transactional control**, **MySQL remains the best fit** for handling **structured, transactional data**, ensuring that inventory counts are **accurate and up to date during high-traffic flash sales**.


### **Deployment Strategy and Challenges**

The current deployment setup hosts **all middleware and backend services on AWS EC2**, with **Kubernetes managing the containerized environment**. This setup allows the system to take advantage of **container orchestration, automated deployments, and resource isolation** while remaining cost-effective. **AWS S3 is used for storing coupon images**, reducing the need for local file storage and enabling seamless access to media assets across different services.

Kubernetes is **deployed within an EC2 instance in a public subnet**, which allows direct external access without requiring additional networking configurations such as a NAT gateway. This approach was **intentionally chosen** to **minimize operational costs** and provide hands-on experience in **managing Kubernetes manually**. Since managed Kubernetes services like **AWS EKS (Elastic Kubernetes Service)** come with additional costs, this self-managed Kubernetes setup serves as a training ground for **deploying, scaling, and maintaining a containerized application in the cloud**.

However, **this setup has several critical limitations**. One major issue is the **lack of horizontal scaling and high availability**. Since all workloads run within **a single EC2 instance**, the system is **unable to scale dynamically** based on traffic demand. If the instance reaches its resource limits, additional requests will be **delayed or rejected**, causing performance degradation. Additionally, if the EC2 instance fails, **the entire application goes down**, leading to downtime and loss of service availability.

Another **significant concern** is **security**. Deploying the entire infrastructure in a **public subnet** exposes backend services directly to the internet, increasing the risk of **unauthorized access, DDoS attacks, and data breaches**. Ideally, backend services should be deployed in a **private subnet**, with only necessary endpoints exposed via a **load balancer or API Gateway**. However, configuring **a private subnet** requires **a NAT gateway or VPC peering**, which adds complexity and cost.

To address these challenges, **a migration to AWS EKS is planned**. **EKS provides managed Kubernetes services**, automatically handling **control plane operations, scalability, and security**. With EKS, workloads can be deployed in **multiple availability zones**, ensuring **fault tolerance** and **automatic scaling** based on resource demand. Additionally, **services will be moved to a private subnet**, improving security by **restricting direct internet access**. Instead, an **Elastic Load Balancer (ELB)** will be used to securely route traffic to backend services.

While the **current setup is functional and cost-efficient for learning**, it is not **suitable for production deployment** due to **scalability, availability, and security risks**. Migrating to **EKS with private networking and auto-scaling** will ensure that the system remains **highly available, secure, and capable of handling fluctuating workloads dynamically**.


# Conclusion

The flash sale system is designed to handle **high concurrency, ensure transactional consistency, and provide a seamless user experience** while maintaining **performance, security, and scalability**. Key challenges such as **preventing overselling, optimizing query performance, managing real-time payment updates, and handling cache breakdowns** have been addressed through **efficient caching, distributed locking, and asynchronous processing**.

While the current deployment is cost-effective for development and learning purposes, it lacks **horizontal scaling, high availability, and security best practices**. Future improvements, including **migrating to AWS EKS, implementing Elasticsearch for better search performance, and enhancing monitoring with Prometheus and Grafana**, will ensure that the system remains **resilient and scalable for real-world production use**.

 
