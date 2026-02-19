# E-Commerce Microservices - Extension & Enhancement Plan

**Document Version:** 1.0  
**Date:** February 17, 2026  
**Author:** System Architecture Team  
**Status:** Planning Phase

---

## 📋 Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current Architecture](#current-architecture)
3. [Proposed Architecture](#proposed-architecture)
4. [Detailed Component Analysis](#detailed-component-analysis)
5. [Implementation Phases](#implementation-phases)
6. [Technical Specifications](#technical-specifications)
7. [Migration Strategy](#migration-strategy)
   - 7.1 [Zero-Downtime Deployment](#71-zero-downtime-deployment-approach)
   - 7.2 [Database Migration](#72-database-migration-strategy)
   - 7.3 [Rollback Plan](#73-rollback-plan)
   - 7.4 [Remote SSH Deployment Strategy](#74-remote-ssh-deployment-strategy)
8. [Risk Assessment](#risk-assessment)
9. [Timeline & Resource Estimation](#timeline--resource-estimation)

---

## 1. Executive Summary

### Current State
- ✅ **2 Microservices**: User Auth (8081), Product Catalog (8080)
- ✅ **Technologies**: Spring Boot 3.5.4, Java 21, MySQL, MongoDB, Redis, Kafka
- ✅ **Communication**: Feign (sync), Kafka (async)
- ✅ **Security**: JWT authentication with RBAC
- ✅ **Additional**: GCP Pub/Sub POC for vehicle messaging

### Proposed Enhancements
- 🎯 **6 New Services**: Orders, Payments, Inventory, Notifications, Gateway, Service Registry
- 🎯 **Advanced Patterns**: Saga, Event Sourcing, CQRS, Circuit Breakers
- 🎯 **Observability**: Distributed tracing, centralized logging, metrics
- 🎯 **Search**: Elasticsearch integration
- 🎯 **Deployment**: Kubernetes-ready with auto-scaling

### Business Value
- **Complete E-commerce Flow**: Cart → Order → Payment → Fulfillment
- **Improved Reliability**: 99.9% uptime with circuit breakers and retries
- **Better Performance**: Sub-100ms response times with multi-level caching
- **Enhanced UX**: Real-time notifications, advanced search, recommendations

---

## 2. Current Architecture

### 2.1 Architecture Diagram (As-Is)

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                                │
│  (Web Browser, Mobile App, Postman, Hoppscotch)                     │
└────────────┬────────────────────────────────────────────┬───────────┘
             │                                            │
             │ HTTP/REST                                  │ HTTP/REST
             │ JWT Token                                  │ JWT Token
             ▼                                            ▼
┌────────────────────────────┐              ┌─────────────────────────────┐
│   USER-AUTH SERVICE        │              │  PRODUCT-CATALOG SERVICE    │
│   Port: 8081               │◄─────────────┤  Port: 8080                 │
│                            │  Feign (HTTP)│                             │
│  Components:               │              │  Components:                │
│  • AuthController          │              │  • ProductController        │
│  • UserController          │              │  • ReviewController         │
│  • JWT Authentication      │              │  • ProductService (Cache)   │
│  • ReviewEventConsumer     │              │  • ReviewService            │
│  • UserRepository          │              │  • UserServiceClient        │
│                            │              │  • KafkaTemplate            │
│  Database: MySQL           │              │                             │
│  - users                   │              │  Databases:                 │
│  - user_roles              │              │  • MySQL (products)         │
│                            │              │  • MongoDB (reviews)        │
└──────────┬─────────────────┘              │  • Redis (cache)            │
           │                                └──────────┬──────────────────┘
           │                                           │
           │ Consumes                                  │ Produces
           │ review-added-events                       │ review-added-events
           │                                           │
           └───────────────┐               ┌───────────┘
                           │               │
                           ▼               ▼
              ┌────────────────────────────────────┐
              │      APACHE KAFKA (9092)           │
              │      Topic: review-added-events    │
              │                                    │
              │      Zookeeper (2181)              │
              └────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    INFRASTRUCTURE LAYER                             │
├─────────────────┬─────────────────┬──────────────┬──────────────────┤
│  MySQL:3306     │  MongoDB:27017  │  Redis:6379  │  Kafka:9092      │
│  • user_db      │  • product_     │  • Cache     │  • Zookeeper     │
│  • product_db   │    reviews      │  • TTL: 60s  │    :2181         │
└─────────────────┴─────────────────┴──────────────┴──────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    ADDITIONAL POC PROJECT                           │
│  PubSub POC (Vehicle Messaging)                                     │
│  • GCP Pub/Sub with attribute filtering                             │
│  • Redis fleet vehicle lookup                                       │
│  • Vehicle event publishing with customer attributes                │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 Current Data Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│  FLOW 1: USER REGISTRATION & LOGIN                                  │
└─────────────────────────────────────────────────────────────────────┘

User → POST /api/auth/register
       ↓
   AuthController
       ↓
   1. Check username exists
   2. Hash password (BCrypt)
   3. Save to MySQL
   4. Assign ROLE_USER
       ↓
   Response: "User registered successfully"

User → POST /api/auth/login
       ↓
   AuthController
       ↓
   1. Authenticate (AuthenticationManager)
   2. Load UserDetails
   3. Generate JWT token (HS256)
       ↓
   Response: { token: "eyJhbGc..." }


┌─────────────────────────────────────────────────────────────────────┐
│  FLOW 2: PRODUCT MANAGEMENT WITH CACHING                            │
└─────────────────────────────────────────────────────────────────────┘

GET /api/products/{id} + JWT
       ↓
   JWT Filter validates token
       ↓
   ProductController
       ↓
   ProductService.getById()
       ↓
   Check Redis cache
       ├─ Cache HIT → Return cached data (5ms)
       └─ Cache MISS → Query MySQL (50ms)
                    → Store in Redis
                    → Return data


┌─────────────────────────────────────────────────────────────────────┐
│  FLOW 3: ADD REVIEW (CROSS-SERVICE + KAFKA EVENT)                   │
└─────────────────────────────────────────────────────────────────────┘

POST /api/reviews + JWT
{
  "productId": 1,
  "userId": 101,
  "rating": 5,
  "comment": "Excellent product!"
}
       ↓
   JWT Filter (extract roles)
       ↓
   ReviewController (@PreAuthorize)
       ↓
   ReviewService.addReview()
       ↓
   ┌──────────────────────────────────┐
   │ 1. Validate User (Feign Call)    │
   │    GET http://localhost:8081/    │
   │         api/users/101            │
   │    ↓                             │
   │    User Service → MySQL query    │
   │    ↓                             │
   │    Return UserDto or 404         │
   └──────────────────────────────────┘
       ↓ User exists ✓
   2. Save review to MongoDB
   3. Publish to Kafka
       ↓
   KafkaTemplate.send("review-added-events", event)
       ↓
   ┌──────────────────────────────────┐
   │   KAFKA BROKER                   │
   │   Topic: review-added-events     │
   └──────────────────────────────────┘
       ↓
   User Service (ReviewEventConsumer)
       ↓
   @KafkaListener
       ↓
   Log event (TODO: Send email/SMS)
```

### 2.3 Technology Stack (Current)

| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| **Language** | Java | 21 | Modern language features |
| **Framework** | Spring Boot | 3.5.4 | Microservices foundation |
| **Security** | Spring Security | 6.5.2 | Authentication/Authorization |
| **JWT** | jjwt | 0.12.6 | Token generation/validation |
| **Database (Relational)** | MySQL | 8.0 | Users, Products |
| **Database (Document)** | MongoDB | 5.0 | Reviews |
| **Cache** | Redis | 7.0 | Product caching |
| **Messaging** | Apache Kafka | 3.0 | Event streaming |
| **Service Communication** | OpenFeign | 4.3.0 | REST client |
| **Build Tool** | Gradle | 8.10 | Dependency management |
| **Testing** | JUnit 5 + PITest | - | Unit + Mutation testing |
| **Documentation** | OpenAPI/Swagger | 3.0 | API documentation |
| **Cloud (POC)** | GCP Pub/Sub | - | Vehicle messaging |

### 2.4 Current Limitations

#### Architectural Gaps
1. ❌ **No Service Discovery**: Hardcoded URLs in Feign client
2. ❌ **No Circuit Breakers**: Service failures cascade
3. ❌ **No API Gateway**: Multiple ports, no centralized security
4. ❌ **No Distributed Tracing**: Hard to debug cross-service issues
5. ❌ **No Centralized Logging**: Logs scattered across services

#### Functional Gaps
1. ❌ **No Order Management**: Can't complete purchases
2. ❌ **No Payment Integration**: No revenue generation
3. ❌ **No Inventory Tracking**: Overselling risk
4. ❌ **No Notifications**: No email/SMS alerts
5. ❌ **No Search**: Basic SQL queries, no full-text search

#### Operational Gaps
1. ❌ **Manual Scaling**: No auto-scaling capability
2. ❌ **No Health Monitoring**: Limited observability
3. ❌ **No Deployment Automation**: Manual deployments
4. ❌ **No Rollback Strategy**: Risky releases

---

## 3. Proposed Architecture

### 3.1 Target Architecture Diagram (To-Be)

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                                │
│  (Web, Mobile, Admin Dashboard, Third-party APIs)                   │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            │ HTTPS (TLS 1.3)
                            │ Single Entry Point
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY (Spring Cloud Gateway)               │
│                    Port: 8080 (External facing)                     │
│  • JWT Validation                  • Rate Limiting (100/min)        │
│  • Request Routing                 • CORS Handling                  │
│  • Load Balancing                  • Circuit Breaker                │
│  • Response Caching                • Request Logging                │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │
                    ┌─────────────┼──────────────┐
                    │             │              │
                    ▼             ▼              ▼
┌──────────────────────────────────────────────────────────────────────┐
│              SERVICE REGISTRY (Netflix Eureka)                       │
│              Port: 8761                                              │
│  • Service Discovery            • Health Checks                      │
│  • Load Balancing               • Service Metadata                   │
└──────────────────────────────────────────────────────────────────────┘
                    │
        ┌───────────┼───────────┬───────────┬──────────┬──────────┐
        │           │           │           │          │          │
        ▼           ▼           ▼           ▼          ▼          ▼
┌─────────────┐ ┌──────────┐ ┌─────────┐ ┌────────┐ ┌──────────┐ ┌────────────┐
│   USER      │ │ PRODUCT  │ │  ORDER  │ │PAYMENT │ │INVENTORY │ │NOTIFICATION│
│   SERVICE   │ │ SERVICE  │ │ SERVICE │ │SERVICE │ │ SERVICE  │ │  SERVICE   │
│   :8081     │ │  :8082   │ │  :8083  │ │ :8084  │ │  :8085   │ │   :8086    │
└──────┬──────┘ └────┬─────┘ └────┬────┘ └───┬────┘ └────┬─────┘ └─────┬──────┘
       │             │            │          │           │             │
       └─────────────┴────────────┴──────────┴───────────┴─────────────┘
                                  │
                                  │ All services publish/consume
                                  ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    EVENT BUS (Apache Kafka)                          │
│                    Port: 9092                                        │
│  Topics:                                                             │
│  • review-added-events          • order-created-events               │
│  • order-paid-events            • order-shipped-events               │
│  • inventory-reserved-events    • payment-completed-events           │
│  • notification-events          • inventory-updated-events           │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│                    DATA LAYER (Polyglot Persistence)                 │
├──────────────┬──────────────┬──────────────┬────────────────────────-┤
│ MySQL        │ MongoDB      │ PostgreSQL   │ Redis                   │
│ • Users      │ • Reviews    │ • Orders     │ • Cache (L2)            │
│ • Products   │ • Logs       │ • Events     │ • Session               │
│              │              │              │ • Rate Limiter          │
└──────────────┴──────────────┴──────────────┴────────────────────────-┘

┌──────────────────────────────────────────────────────────────────────┐
│                    SEARCH LAYER                                      │
│  Elasticsearch :9200                                                 │
│  • Product Full-Text Search    • Faceted Navigation                  │
│  • Auto-complete                • Search Analytics                   │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│                    OBSERVABILITY STACK                               │
├─────────────────────┬─────────────────────┬──────────────────────────┤
│ Zipkin :9411        │ ELK Stack           │ Prometheus + Grafana     │
│ • Distributed       │ • Centralized       │ • Metrics                │
│   Tracing           │   Logging           │ • Dashboards             │
│ • Performance       │ • Search            │ • Alerts                 │
│   Analysis          │ • Visualization     │                          │
└─────────────────────┴─────────────────────┴──────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│                    EXTERNAL INTEGRATIONS                             │
├──────────────────┬──────────────────┬────────────────────────────────┤
│ Payment Gateway  │ Email Service    │ SMS Service                    │
│ • Stripe         │ • AWS SES        │ • Twilio                       │
│ • PayPal         │ • SendGrid       │ • AWS SNS                      │
└──────────────────┴──────────────────┴────────────────────────────────┘
```

### 3.2 Service Interaction Patterns

```
┌─────────────────────────────────────────────────────────────────────┐
│  PATTERN 1: SYNCHRONOUS (Request/Response via Feign + Eureka)       │
└─────────────────────────────────────────────────────────────────────┘

Order Service needs user info:
    OrderService
        ↓ (Feign Client)
    Eureka Discovery (finds User Service instances)
        ↓ (Load balanced)
    User Service → Returns user data
        ↓
    Order Service continues processing


┌─────────────────────────────────────────────────────────────────────┐
│  PATTERN 2: ASYNCHRONOUS (Event-Driven via Kafka)                   │
└─────────────────────────────────────────────────────────────────────┘

Order Saga Flow (Happy Path):

1. Order Service
   ↓ Publish: OrderCreatedEvent
   Kafka Topic: order-created-events

2. Inventory Service (Listener)
   ↓ Consumes: OrderCreatedEvent
   ↓ Reserve stock
   ↓ Publish: InventoryReservedEvent

3. Payment Service (Listener)
   ↓ Consumes: InventoryReservedEvent
   ↓ Process payment (Stripe API)
   ↓ Publish: PaymentCompletedEvent

4. Order Service (Listener)
   ↓ Consumes: PaymentCompletedEvent
   ↓ Update order status: PAID
   ↓ Publish: OrderPaidEvent

5. Notification Service (Listener)
   ↓ Consumes: OrderPaidEvent
   ↓ Send email + SMS confirmation


┌─────────────────────────────────────────────────────────────────────┐
│  PATTERN 3: SAGA PATTERN (Compensating Transactions)                │
└─────────────────────────────────────────────────────────────────────┘

Order Saga Flow (Payment Failure):

1. Order Created → Inventory Reserved
2. Payment Processing... FAILED ❌
3. Compensation Actions:
   ↓ Publish: PaymentFailedEvent
   ↓ Inventory Service: Release reserved stock
   ↓ Order Service: Update status → CANCELLED
   ↓ Notification Service: Send failure email


┌─────────────────────────────────────────────────────────────────────┐
│  PATTERN 4: CIRCUIT BREAKER (Resilience)                            │
└─────────────────────────────────────────────────────────────────────┘

Review Service calls User Service via Feign:

@CircuitBreaker(name = "userService", fallbackMethod = "getUserFallback")
public UserDto getUser(Long userId) {
    return userServiceClient.getUserById(userId);
}

States:
┌─────────┐  Success  ┌──────┐  Threshold    ┌──────┐
│ CLOSED  │─────────→ │ OPEN │←──────────────│ HALF │
└─────────┘           └──────┘ Failed        │ OPEN │
     ↑                   │                   └──────┘
     │                   │ Wait duration         │
     └───────────────────┴───────────────────────┘
```

### 3.3 New Services Detailed

#### Service: Order Service (Port 8083)

**Purpose:** Manage customer orders and order lifecycle

**Endpoints:**
- `POST /api/orders` - Create new order
- `GET /api/orders/{id}` - Get order details
- `GET /api/orders/user/{userId}` - Get user's orders
- `PUT /api/orders/{id}/cancel` - Cancel order

**Database:** PostgreSQL (orders, order_items, order_events)

**Events Published:**
- `OrderCreatedEvent`
- `OrderPaidEvent`
- `OrderShippedEvent`
- `OrderCancelledEvent`

**Events Consumed:**
- `PaymentCompletedEvent`
- `PaymentFailedEvent`
- `InventoryReservedEvent`
- `InventoryReservationFailedEvent`

**Dependencies:**
- Product Service (get product details via Feign)
- User Service (validate user via Feign)
- Inventory Service (check stock via events)
- Payment Service (process payment via events)

---

#### Service: Payment Service (Port 8084)

**Purpose:** Handle payment processing and transaction management

**Endpoints:**
- `POST /api/payments` - Process payment
- `GET /api/payments/{id}` - Get payment status
- `POST /api/payments/{id}/refund` - Refund payment

**Database:** PostgreSQL (payments, transactions)

**External Integrations:**
- Stripe API
- PayPal SDK

**Events Published:**
- `PaymentCompletedEvent`
- `PaymentFailedEvent`
- `RefundCompletedEvent`

**Events Consumed:**
- `InventoryReservedEvent`

---

#### Service: Inventory Service (Port 8085)

**Purpose:** Manage product stock and reservations

**Endpoints:**
- `GET /api/inventory/{productId}` - Get stock level
- `POST /api/inventory/reserve` - Reserve stock
- `POST /api/inventory/release` - Release reservation
- `PUT /api/inventory/{productId}/stock` - Update stock

**Database:** MySQL (inventory, reservations)

**Events Published:**
- `InventoryReservedEvent`
- `InventoryReservationFailedEvent`
- `InventoryUpdatedEvent`

**Events Consumed:**
- `OrderCreatedEvent`
- `OrderCancelledEvent`
- `PaymentFailedEvent`

---

#### Service: Notification Service (Port 8086)

**Purpose:** Send email, SMS, and push notifications

**Endpoints:**
- `POST /api/notifications/email` - Send email
- `POST /api/notifications/sms` - Send SMS
- `GET /api/notifications/{userId}` - Get notification history

**Database:** MongoDB (notification_logs)

**External Integrations:**
- AWS SES / SendGrid (Email)
- Twilio / AWS SNS (SMS)
- Firebase Cloud Messaging (Push)

**Events Consumed:**
- `ReviewAddedEvent`
- `OrderCreatedEvent`
- `OrderPaidEvent`
- `OrderShippedEvent`
- `PaymentCompletedEvent`
- `PaymentFailedEvent`

**Templates:**
- Review confirmation
- Order confirmation
- Payment receipt
- Shipping notification
- Order cancellation

---

## 4. Detailed Component Analysis

### 4.1 Changes to Existing Services

#### User Service (e-com-user-auth)

**Current State:**
```java
// Basic user model
@Entity
public class User {
    private Long id;
    private String username;
    private String email;
    private String password;
    private Set<String> roles;
}
```

**Proposed Changes:**

**1. Add Contact Information:**
```java
@Entity
public class User {
    private Long id;
    private String username;
    private String email;
    private String phone;          // NEW: For SMS notifications
    private String firstName;       // NEW: For personalization
    private String lastName;        // NEW: For personalization
    private String password;
    private Set<String> roles;
    
    // NEW: Preferences
    @Embedded
    private NotificationPreferences preferences;
    
    // NEW: Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
}

@Embeddable
public class NotificationPreferences {
    private boolean emailEnabled = true;
    private boolean smsEnabled = false;
    private boolean pushEnabled = false;
}
```

**2. Add Profile Management Endpoints:**
```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    // NEW ENDPOINTS
    @GetMapping("/profile")
    public UserDto getCurrentUserProfile();
    
    @PutMapping("/profile")
    public UserDto updateProfile(@RequestBody UpdateProfileRequest request);
    
    @PutMapping("/preferences")
    public void updateNotificationPreferences(@RequestBody NotificationPreferences prefs);
}
```

**3. Enhanced Security:**
```java
// Add refresh token support
@Entity
public class RefreshToken {
    private String token;
    private Long userId;
    private LocalDateTime expiryDate;
}
```

**Build.gradle additions:**
```gradle
// Add notification dependencies
implementation 'org.springframework.boot:spring-boot-starter-mail'
// Optional: implementation 'com.twilio.sdk:twilio:9.14.1'
```

---

#### Product Service (e-com-product-catalog)

**Current State:**
- Basic CRUD for products
- Redis caching
- MongoDB for reviews

**Proposed Changes:**

**1. Add Inventory Integration:**
```java
@Service
public class ProductService {
    @Autowired
    private InventoryServiceClient inventoryClient;
    
    public ProductWithStock getProductWithStock(Long id) {
        Product product = getProductById(id);
        Integer stock = inventoryClient.getStock(id);
        return new ProductWithStock(product, stock);
    }
}
```

**2. Add Product Rating Cache:**
```java
// Compute average rating from reviews
public ProductRating calculateRating(Long productId) {
    List<Review> reviews = reviewRepository.findByProductId(productId);
    double avg = reviews.stream()
        .mapToInt(Review::getRating)
        .average()
        .orElse(0.0);
    return new ProductRating(productId, avg, reviews.size());
}
```

**3. Event Publishing for Stock Changes:**
```java
@KafkaListener(topics = "inventory-updated")
public void handleInventoryUpdate(InventoryUpdatedEvent event) {
    // Invalidate product cache
    cacheManager.evict("products", event.getProductId());
}
```

**Build.gradle additions:**
```gradle
// Add Elasticsearch for search
implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
```

---

### 4.2 Infrastructure Components

#### API Gateway Configuration

**application.yml:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        # User Service Routes
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/auth/**, /api/users/**
          filters:
            - name: RateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
            - name: CircuitBreaker
              args:
                name: userServiceBreaker
                fallbackUri: forward:/fallback/user
        
        # Product Service Routes
        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/products/**, /api/reviews/**
          filters:
            - ResponseCache
            - name: Retry
              args:
                retries: 3
                statuses: BAD_GATEWAY, SERVICE_UNAVAILABLE
        
        # Order Service Routes
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - name: RequestRateLimiter
              args:
                deny-empty-key: false
```

---

#### Eureka Server Configuration

**Main Class:**
```java
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }
}
```

**application.yml:**
```yaml
server:
  port: 8761

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    enable-self-preservation: true
```

---

#### Observability Stack

**Zipkin Configuration (Each Service):**
```yaml
spring:
  zipkin:
    base-url: http://localhost:9411
  sleuth:
    sampler:
      probability: 1.0  # 100% sampling for dev
```

**Prometheus Configuration:**
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'spring-actuator'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8081', 'localhost:8082', 'localhost:8083']
```

**Each Service application.yml:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## 5. Implementation Phases

### Phase 1: Foundation (Weeks 1-2) 🟢 START HERE

**Objective:** Add infrastructure services and improve reliability

**Tasks:**

**Week 1:**
1. ✅ Create Eureka Server project
   - Dependencies: `spring-cloud-starter-netflix-eureka-server`
   - Configure port 8761
   - Test service registration

2. ✅ Update existing services to register with Eureka
   - Add dependency: `spring-cloud-starter-netflix-eureka-client`
   - Configure `spring.application.name`
   - Configure `eureka.client.service-url`

3. ✅ Update Feign clients to use service discovery
   - Remove hardcoded URLs
   - Use `@FeignClient(name = "user-service")`

**Week 2:**
4. ✅ Add Circuit Breakers with Resilience4j
   - Dependency: `resilience4j-spring-boot3`
   - Configure fallback methods
   - Add retry mechanism

5. ✅ Create API Gateway
   - Project: `spring-cloud-starter-gateway`
   - Configure routes for existing services
   - Add rate limiting

6. ✅ Testing
   - Test service discovery
   - Test circuit breaker behavior
   - Load test gateway

**Deliverables:**
- Working Eureka Server
- Services auto-discovering each other
- API Gateway routing requests
- Circuit breakers protecting services

**Estimated Effort:** 40-50 hours

---

### Phase 2: Notification Service (Weeks 3-4)

**Objective:** Complete the TODO - send email/SMS notifications

**Tasks:**

**Week 3:**
1. ✅ Update User model
   - Add `phone`, `firstName`, `lastName`
   - Add `NotificationPreferences`
   - Database migration script

2. ✅ Create Notification Service
   - New Spring Boot project
   - Register with Eureka
   - Configure email (AWS SES or SMTP)
   - Configure SMS (Twilio)

3. ✅ Implement NotificationService class
   - Email templates (Thymeleaf)
   - SMS message builder
   - Async processing with `@Async`

**Week 4:**
4. ✅ Update ReviewEventConsumer
   - Fetch user details
   - Call NotificationService
   - Handle errors gracefully

5. ✅ Create notification templates
   - HTML email templates
   - SMS message templates
   - Support for multiple languages

6. ✅ Testing
   - Unit tests for NotificationService
   - Integration tests with Kafka
   - Real email/SMS testing

**Deliverables:**
- Notification Service deployed
- Email notifications working
- SMS notifications working (optional)
- Template management

**Estimated Effort:** 35-45 hours

---

### Phase 3: Order Management (Weeks 5-7)

**Objective:** Enable complete purchase flow

**Tasks:**

**Week 5:**
1. ✅ Create Order Service skeleton
   - Spring Boot project
   - PostgreSQL database setup
   - Entity models (Order, OrderItem)
   - Basic CRUD operations

2. ✅ Implement order creation
   - Validate products (call Product Service)
   - Validate user (call User Service)
   - Calculate totals
   - Save to database

**Week 6:**
3. ✅ Create Payment Service skeleton
   - Spring Boot project
   - PostgreSQL database setup
   - Stripe SDK integration
   - Payment entity model

4. ✅ Implement payment processing
   - Create Stripe payment intent
   - Handle webhooks
   - Publish PaymentCompletedEvent

**Week 7:**
5. ✅ Create Inventory Service
   - Spring Boot project
   - MySQL database setup
   - Stock reservation logic
   - Event listeners

6. ✅ Implement Saga pattern
   - Order orchestration
   - Compensating transactions
   - Event choreography

7. ✅ Integration testing
   - End-to-end order flow testing
   - Failure scenario testing
   - Performance testing

**Deliverables:**
- Order Service operational
- Payment integration working
- Inventory management functional
- Saga pattern implemented

**Estimated Effort:** 70-85 hours

---

### Phase 4: Observability (Weeks 8-9)

**Objective:** Add monitoring, logging, and tracing

**Tasks:**

**Week 8:**
1. ✅ Setup Zipkin
   - Docker container
   - Configure all services
   - Test trace visualization

2. ✅ Setup ELK Stack
   - Elasticsearch, Logstash, Kibana
   - Configure log shipping
   - Create dashboards

3. ✅ Add structured logging
   - JSON log format
   - Correlation IDs
   - MDC context

**Week 9:**
4. ✅ Setup Prometheus + Grafana
   - Prometheus for metrics collection
   - Grafana dashboards
   - Alert rules

5. ✅ Custom metrics
   - Business metrics (orders/hour, revenue)
   - Technical metrics (response time, error rate)
   - JVM metrics

6. ✅ Documentation
   - Runbooks for common issues
   - Dashboard user guide
   - Alert response procedures

**Deliverables:**
- Distributed tracing operational
- Centralized logging with search
- Metrics and dashboards
- Alert system configured

**Estimated Effort:** 45-55 hours

---

### Phase 5: Search & Performance (Weeks 10-11)

**Objective:** Add advanced search and optimize performance

**Tasks:**

**Week 10:**
1. ✅ Setup Elasticsearch
   - Docker container
   - Create indices
   - Configure mappings

2. ✅ Sync products to Elasticsearch
   - Initial bulk import
   - Real-time updates via Kafka
   - Handle failures

3. ✅ Implement search API
   - Full-text search
   - Faceted search
   - Auto-complete

**Week 11:**
4. ✅ Performance optimization
   - Add Caffeine cache (L1)
   - Optimize database queries
   - Add database indexes

5. ✅ Load testing
   - JMeter test scripts
   - Gatling scenarios
   - Performance benchmarks

6. ✅ Optimization based on results
   - Adjust cache TTLs
   - Optimize slow queries
   - Scale services

**Deliverables:**
- Elasticsearch search working
- Multi-level caching
- Performance benchmarks documented
- Optimized configuration

**Estimated Effort:** 40-50 hours

---

### Phase 6: Advanced Features (Weeks 12-14)

**Objective:** Add real-time features and ML

**Tasks:**

**Week 12:**
1. ✅ Add WebSocket support
   - Configure STOMP
   - Order tracking updates
   - Stock alerts

2. ✅ Implement real-time features
   - Live order status
   - Admin dashboard updates
   - Chat support (optional)

**Week 13:**
3. ✅ ML Recommendation Service
   - Python Flask service
   - Collaborative filtering model
   - Integration with Product Service

4. ✅ Data pipeline for ML
   - Export user behavior data
   - Train recommendation model
   - Deploy model

**Week 14:**
5. ✅ Event Sourcing for Orders
   - Event store implementation
   - Rebuild state from events
   - Snapshot mechanism

6. ✅ CQRS pattern
   - Separate read/write models
   - Denormalized views
   - Event handlers

**Deliverables:**
- WebSocket real-time updates
- ML recommendation engine
- Event Sourcing implemented
- CQRS pattern applied

**Estimated Effort:** 75-90 hours

---

### Phase 7: Cloud, DevOps & Remote SSH Deployment (Weeks 15-17)

**Objective:** Prepare for production deployment with automated SSH-based and container-based strategies

**Tasks:**

**Week 15:**
1. ✅ Kubernetes manifests
   - Deployments for all services
   - Services and Ingress
   - ConfigMaps and Secrets

2. ✅ Helm charts
   - Chart for each service
   - Values for dev/staging/prod
   - Deployment scripts

3. ✅ CI/CD pipeline
   - GitHub Actions workflows
   - Docker image building
   - Automated testing

**Week 16:**
4. ✅ Production checklist
   - HTTPS configuration
   - Secret management (Vault)
   - Backup strategy
   - Disaster recovery plan

5. ✅ Performance testing
   - Production-like environment
   - Load testing
   - Chaos engineering

**Week 17: Remote SSH Deployment**
6. ✅ SSH Infrastructure Setup
   - Generate deployment SSH key pairs (Ed25519)
   - Configure target servers (dev, staging, prod)
   - Set up SSH config with bastion/jump host
   - Install systemd service units on targets
   - Harden SSH: disable password auth, restrict users

7. ✅ Automated SSH Deployment Scripts
   - Multi-service deploy script with health checks
   - Rolling deployment (zero-downtime via SSH)
   - Remote database migration runner
   - Service log tailing and diagnostics via SSH
   - Rollback automation

8. ✅ CI/CD SSH Integration
   - GitHub Actions SSH deploy workflow
   - SSH key management with GitHub Secrets
   - Post-deploy smoke tests over SSH
   - Slack/Teams notifications on deploy status

9. ✅ Documentation
   - Architecture documentation
   - SSH deployment guide & runbook
   - Operations manual
   - SSH key rotation procedures

**Deliverables:**
- Kubernetes deployment ready
- CI/CD pipeline operational (with SSH deploy option)
- Remote SSH deployment fully automated
- Production checklist completed
- Complete documentation

**Estimated Effort:** 60-75 hours

---

## 6. Technical Specifications

### 6.1 Database Schema Changes

#### User Service - users table

**Current Schema:**
```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    user_id BIGINT,
    role VARCHAR(50),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Proposed Schema:**
```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20),                          -- NEW
    first_name VARCHAR(100),                    -- NEW
    last_name VARCHAR(100),                     -- NEW
    password VARCHAR(255) NOT NULL,
    email_notifications BOOLEAN DEFAULT TRUE,   -- NEW
    sms_notifications BOOLEAN DEFAULT FALSE,    -- NEW
    push_notifications BOOLEAN DEFAULT FALSE,   -- NEW
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,                       -- NEW
    INDEX idx_email (email),
    INDEX idx_username (username)
);

CREATE TABLE user_roles (
    user_id BIGINT,
    role VARCHAR(50),
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user_id (user_id)
);

CREATE TABLE refresh_tokens (                  -- NEW
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_token (token),
    INDEX idx_user_id (user_id)
);
```

**Migration Script:**
```sql
-- Add new columns to existing users table
ALTER TABLE users
    ADD COLUMN phone VARCHAR(20),
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name VARCHAR(100),
    ADD COLUMN email_notifications BOOLEAN DEFAULT TRUE,
    ADD COLUMN sms_notifications BOOLEAN DEFAULT FALSE,
    ADD COLUMN push_notifications BOOLEAN DEFAULT FALSE,
    ADD COLUMN last_login TIMESTAMP;

-- Add indexes
CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_username ON users(username);

-- Create new table
CREATE TABLE refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_token (token),
    INDEX idx_user_id (user_id)
);
```

---

#### Order Service - PostgreSQL

```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) NOT NULL,  -- PENDING, PAID, SHIPPED, DELIVERED, CANCELLED
    payment_id VARCHAR(255),
    shipping_address TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id)
);

CREATE TABLE order_events (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    INDEX idx_order_id (order_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
);
```

---

#### Payment Service - PostgreSQL

```sql
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    payment_id VARCHAR(255) UNIQUE NOT NULL,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(50) NOT NULL,  -- PENDING, COMPLETED, FAILED, REFUNDED
    payment_method VARCHAR(50),   -- STRIPE, PAYPAL
    stripe_payment_intent_id VARCHAR(255),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_stripe_payment_intent_id (stripe_payment_intent_id)
);

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,  -- CHARGE, REFUND
    amount DECIMAL(10,2) NOT NULL,
    transaction_id VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES payments(id),
    INDEX idx_payment_id (payment_id),
    INDEX idx_transaction_type (transaction_type)
);
```

---

#### Inventory Service - MySQL

```sql
CREATE TABLE inventory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT UNIQUE NOT NULL,
    available_stock INTEGER NOT NULL DEFAULT 0,
    reserved_stock INTEGER NOT NULL DEFAULT 0,
    total_stock INTEGER GENERATED ALWAYS AS (available_stock + reserved_stock) STORED,
    reorder_level INTEGER DEFAULT 10,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id),
    INDEX idx_available_stock (available_stock)
);

CREATE TABLE reservations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reservation_id VARCHAR(255) UNIQUE NOT NULL,
    product_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,  -- RESERVED, CONFIRMED, RELEASED
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES inventory(product_id),
    INDEX idx_order_id (order_id),
    INDEX idx_status (status),
    INDEX idx_expires_at (expires_at)
);
```

---

### 6.2 Kafka Topics Configuration

```yaml
# Kafka Topics definition
topics:
  - name: review-added-events
    partitions: 3
    replication-factor: 2
    
  - name: order-created-events
    partitions: 6
    replication-factor: 2
    
  - name: inventory-reserved-events
    partitions: 6
    replication-factor: 2
    
  - name: payment-completed-events
    partitions: 6
    replication-factor: 2
    
  - name: payment-failed-events
    partitions: 3
    replication-factor: 2
    
  - name: order-paid-events
    partitions: 6
    replication-factor: 2
    
  - name: order-shipped-events
    partitions: 3
    replication-factor: 2
    
  - name: notification-events
    partitions: 6
    replication-factor: 2
    
  - name: inventory-updated-events
    partitions: 3
    replication-factor: 2
```

---

### 6.3 API Specifications

#### Order Service API

```yaml
openapi: 3.0.0
info:
  title: Order Service API
  version: 1.0.0

paths:
  /api/orders:
    post:
      summary: Create new order
      security:
        - bearerAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                items:
                  type: array
                  items:
                    type: object
                    properties:
                      productId:
                        type: integer
                      quantity:
                        type: integer
                shippingAddress:
                  type: string
      responses:
        '201':
          description: Order created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Order'
        '400':
          description: Invalid request
        '401':
          description: Unauthorized

  /api/orders/{orderId}:
    get:
      summary: Get order details
      security:
        - bearerAuth: []
      parameters:
        - name: orderId
          in: path
          required: true
          schema:
            type: integer
      responses:
        '200':
          description: Order details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Order'

components:
  schemas:
    Order:
      type: object
      properties:
        id:
          type: integer
        orderNumber:
          type: string
        userId:
          type: integer
        totalAmount:
          type: number
        status:
          type: string
          enum: [PENDING, PAID, SHIPPED, DELIVERED, CANCELLED]
        items:
          type: array
          items:
            $ref: '#/components/schemas/OrderItem'
        createdAt:
          type: string
          format: date-time
    
    OrderItem:
      type: object
      properties:
        productId:
          type: integer
        productName:
          type: string
        quantity:
          type: integer
        price:
          type: number
```

---

## 7. Migration Strategy

### 7.1 Zero-Downtime Deployment Approach

**Strategy: Blue-Green Deployment**

```
┌─────────────────────────────────────────────────────────────────┐
│  Step 1: Deploy New Services (Green Environment)                │
└─────────────────────────────────────────────────────────────────┘

Existing (Blue):                          New (Green):
User Service v1.0 ----┐                   User Service v2.0 ----┐
Product Service v1.0 -┤                   Product Service v2.0 -┤
                      │                   Order Service v1.0 ---┤
                      ▼                   Payment Service v1.0 -┤
                  Gateway (v1)            Inventory Service ----┤
                      │                   Notification Service -┤
                      │                                         │
                      │                                         ▼
                      │                                    Gateway (v2)
                      │                                         │
                   100% Traffic                            0% Traffic


┌─────────────────────────────────────────────────────────────────┐
│  Step 2: Gradual Traffic Shift                                  │
└─────────────────────────────────────────────────────────────────┘

Week 1: 10% traffic → Green (canary release)
Week 2: 25% traffic → Green
Week 3: 50% traffic → Green
Week 4: 100% traffic → Green

Monitor metrics at each step:
- Error rates < 0.1%
- Latency p95 < 200ms
- Business metrics stable


┌─────────────────────────────────────────────────────────────────┐
│  Step 3: Decommission Blue                                      │
└─────────────────────────────────────────────────────────────────┘

After 1 week of 100% on Green with no issues:
- Keep Blue environment running for 24 hours (rollback capability)
- Archive logs and metrics
- Decommission Blue resources
```

---

### 7.2 Database Migration Strategy

**Flyway Migration Scripts**

```java
// V1__baseline.sql (Document current state)
CREATE TABLE users (...);
CREATE TABLE products (...);

// V2__add_user_contact_info.sql
ALTER TABLE users
    ADD COLUMN phone VARCHAR(20),
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name VARCHAR(100);

// V3__add_notification_preferences.sql
ALTER TABLE users
    ADD COLUMN email_notifications BOOLEAN DEFAULT TRUE,
    ADD COLUMN sms_notifications BOOLEAN DEFAULT FALSE,
    ADD COLUMN push_notifications BOOLEAN DEFAULT FALSE;

// V4__create_orders_schema.sql
CREATE TABLE orders (...);
CREATE TABLE order_items (...);
CREATE TABLE order_events (...);
```

**Configuration:**
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
```

---

### 7.3 Rollback Plan

**Scenario: Critical Issue Discovered in Production**

```
┌─────────────────────────────────────────────────────────────────┐
│  ROLLBACK PROCEDURE (15 minutes)                                │
└─────────────────────────────────────────────────────────────────┘

T+0:00 - Issue detected (alerts firing)
         └─ Incident commander declares rollback

T+0:02 - Switch traffic back to Blue environment
         └─ kubectl apply -f blue-deployment.yaml
         └─ kubectl scale deployment green --replicas=0

T+0:05 - Verify Blue environment stable
         └─ Check error rates
         └─ Check latency metrics
         └─ Verify business KPIs

T+0:10 - Database rollback (if needed)
         └─ Run rollback scripts
         └─ Restore from backup (if necessary)

T+0:15 - Post-rollback verification
         └─ All systems green
         └─ Customer impact assessment
         └─ RCA (Root Cause Analysis) initiated
```

**Database Rollback Strategy:**
```sql
-- Each migration has a corresponding rollback
-- V2__add_user_contact_info.sql
ALTER TABLE users ADD COLUMN phone VARCHAR(20);

-- U2__rollback_user_contact_info.sql
ALTER TABLE users DROP COLUMN phone;
```

---

### 7.4 Remote SSH Deployment Strategy

**Overview:** SSH-based deployment provides a lightweight, secure, and infrastructure-agnostic approach to deploy microservices on bare-metal servers, VMs, or cloud instances without requiring a full Kubernetes cluster.

---

#### 7.4.1 SSH Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DEVELOPER / CI/CD PIPELINE                       │
│  (Local Machine / GitHub Actions / Jenkins)                        │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            │ SSH (Port 22, Ed25519 Key)
                            │ Encrypted Channel (AES-256-GCM)
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    BASTION / JUMP HOST                              │
│                    bastion.ecom.internal                            │
│                    IP: 10.0.0.5                                     │
│  • Hardened SSH (Port 2222)                                        │
│  • MFA enabled                                                      │
│  • Audit logging                                                    │
│  • Rate limiting (MaxStartups 3:50:10)                              │
│  • Only deploy-user allowed                                        │
└────────────┬───────────────┬───────────────┬────────────────────────┘
             │               │               │
             │ SSH Tunnel    │ SSH Tunnel    │ SSH Tunnel
             ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  DEV SERVER     │ │ STAGING SERVER  │ │ PROD CLUSTER    │
│  10.0.1.10      │ │ 10.0.2.10       │ │ 10.0.3.{10-12}  │
│                 │ │                 │ │                 │
│  Services:      │ │  Services:      │ │  Services:      │
│  • user-auth    │ │  • user-auth    │ │  • user-auth    │
│  • product-cat  │ │  • product-cat  │ │  • product-cat  │
│  • order-svc    │ │  • order-svc    │ │  • order-svc    │
│  • payment-svc  │ │  • payment-svc  │ │  • payment-svc  │
│  • inventory    │ │  • inventory    │ │  • inventory    │
│  • notification │ │  • notification │ │  • notification │
│  • gateway      │ │  • gateway      │ │  • gateway      │
│  • eureka       │ │  • eureka       │ │  • eureka       │
└─────────────────┘ └─────────────────┘ └─────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  PRODUCTION CLUSTER DETAIL (3 Nodes)                                │
├─────────────────┬─────────────────┬─────────────────────────────────┤
│  Node 1         │  Node 2         │  Node 3                         │
│  10.0.3.10      │  10.0.3.11      │  10.0.3.12                      │
│                 │                 │                                 │
│  • user-auth    │  • order-svc    │  • gateway                      │
│  • product-cat  │  • payment-svc  │  • eureka                       │
│  • notification │  • inventory    │  • monitoring                   │
│                 │                 │                                 │
│  + MySQL        │  + PostgreSQL   │  + Redis                        │
│  + MongoDB      │  + Kafka        │  + Elasticsearch                │
└─────────────────┴─────────────────┴─────────────────────────────────┘
```

---

#### 7.4.2 SSH Configuration

**~/.ssh/config (Developer Machine):**
```ssh-config
# ─── Bastion / Jump Host ────────────────────────────
Host bastion
    HostName bastion.ecom.internal
    User deploy
    Port 2222
    IdentityFile ~/.ssh/ecom-deploy-ed25519
    ForwardAgent no
    StrictHostKeyChecking yes
    UserKnownHostsFile ~/.ssh/known_hosts_ecom
    ServerAliveInterval 30
    ServerAliveCountMax 3

# ─── Dev Server ─────────────────────────────────────
Host dev
    HostName 10.0.1.10
    User deploy
    Port 22
    ProxyJump bastion
    IdentityFile ~/.ssh/ecom-deploy-ed25519

# ─── Staging Server ─────────────────────────────────
Host staging
    HostName 10.0.2.10
    User deploy
    Port 22
    ProxyJump bastion
    IdentityFile ~/.ssh/ecom-deploy-ed25519

# ─── Production Nodes ───────────────────────────────
Host prod-node1
    HostName 10.0.3.10
    User deploy
    Port 22
    ProxyJump bastion
    IdentityFile ~/.ssh/ecom-deploy-ed25519

Host prod-node2
    HostName 10.0.3.11
    User deploy
    Port 22
    ProxyJump bastion
    IdentityFile ~/.ssh/ecom-deploy-ed25519

Host prod-node3
    HostName 10.0.3.12
    User deploy
    Port 22
    ProxyJump bastion
    IdentityFile ~/.ssh/ecom-deploy-ed25519

# ─── All Production Nodes (Wildcard) ────────────────
Host prod-*
    StrictHostKeyChecking yes
    UserKnownHostsFile ~/.ssh/known_hosts_ecom
    ServerAliveInterval 30
    ServerAliveCountMax 3
```

**Server-side SSH hardening (/etc/ssh/sshd_config):**
```bash
# Disable password authentication
PasswordAuthentication no
ChallengeResponseAuthentication no
UsePAM yes

# Restrict to deploy user only
AllowUsers deploy

# Use only Ed25519 keys
HostKey /etc/ssh/ssh_host_ed25519_key
PubkeyAcceptedAlgorithms ssh-ed25519

# Rate limiting
MaxStartups 3:50:10
MaxAuthTries 3
LoginGraceTime 30

# Logging
LogLevel VERBOSE
SyslogFacility AUTH

# Disable unused features
X11Forwarding no
AllowTcpForwarding no
PermitRootLogin no
PermitEmptyPasswords no
```

---

#### 7.4.3 SSH Key Management

```
┌─────────────────────────────────────────────────────────────────────┐
│               SSH KEY LIFECYCLE                                     │
└─────────────────────────────────────────────────────────────────────┘

1. GENERATION
   ┌────────────────────────────────────────────┐
   │  ssh-keygen -t ed25519                     │
   │    -C "ecom-deploy-$(date +%Y%m)"          │
   │    -f ~/.ssh/ecom-deploy-ed25519           │
   └────────────────────────────────────────────┘

2. DISTRIBUTION (Secure)
   ┌────────────────────────────────────────────┐
   │  ssh-copy-id -i ~/.ssh/ecom-deploy-ed25519 │
   │    deploy@bastion:2222                     │
   │                                            │
   │  Internal: Ansible playbook distributes    │
   │  keys to all target servers                │
   └────────────────────────────────────────────┘

3. ROTATION (Every 90 Days)
   ┌────────────────────────────────────────────┐
   │  Month 1: Generate new key pair            │
   │  Month 1: Add new public key to servers    │
   │  Month 2: Switch CI/CD to new key          │
   │  Month 3: Remove old public key            │
   │  Archive: Store old key in Vault           │
   └────────────────────────────────────────────┘

4. REVOCATION (Emergency)
   ┌────────────────────────────────────────────┐
   │  Remove public key from authorized_keys    │
   │  Restart SSHD on all servers               │
   │  Generate replacement key                  │
   │  Update CI/CD secrets                      │
   │  Audit access logs                         │
   └────────────────────────────────────────────┘
```

---

#### 7.4.4 Deployment Scripts

**Master Deploy Script (deploy.sh):**
```bash
#!/bin/bash
set -euo pipefail

# ═══════════════════════════════════════════════════
# E-Commerce Microservices - SSH Deployment Script
# ═══════════════════════════════════════════════════

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_ROOT="$(dirname "$SCRIPT_DIR")"

# ─── Configuration ─────────────────────────────────
ENV="${1:?Usage: deploy.sh <env> [service] -- env: dev|staging|prod}"
SERVICE="${2:-all}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DEPLOY_DIR="/opt/ecom"
BACKUP_DIR="/opt/ecom/backups/$TIMESTAMP"
LOG_DIR="/opt/ecom/logs"
HEALTH_TIMEOUT=60
HEALTH_INTERVAL=5

# ─── Color Output ──────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC}  $(date '+%H:%M:%S') $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $(date '+%H:%M:%S') $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $(date '+%H:%M:%S') $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%H:%M:%S') $*"; }

# ─── Service Definitions ──────────────────────────
declare -A SERVICE_PORTS=(
    ["user-auth"]=8081
    ["product-catalog"]=8082
    ["order-service"]=8083
    ["payment-service"]=8084
    ["inventory-service"]=8085
    ["notification-service"]=8086
    ["api-gateway"]=8080
    ["eureka-server"]=8761
)

declare -A SERVICE_PROJECTS=(
    ["user-auth"]="e-com-user-auth"
    ["product-catalog"]="e-com-product-catalog"
    ["order-service"]="e-com-order-service"
    ["payment-service"]="e-com-payment-service"
    ["inventory-service"]="e-com-inventory-service"
    ["notification-service"]="e-com-notification-service"
    ["api-gateway"]="e-com-api-gateway"
    ["eureka-server"]="e-com-eureka-server"
)

# ─── Environment → Host Mapping ───────────────────
get_hosts() {
    case "$ENV" in
        dev)     echo "dev" ;;
        staging) echo "staging" ;;
        prod)    echo "prod-node1 prod-node2 prod-node3" ;;
        *) log_error "Unknown env: $ENV"; exit 1 ;;
    esac
}

# ─── Service → Host Mapping (Production) ──────────
get_service_host() {
    local svc="$1"
    case "$svc" in
        user-auth|product-catalog|notification-service) echo "prod-node1" ;;
        order-service|payment-service|inventory-service) echo "prod-node2" ;;
        api-gateway|eureka-server)                       echo "prod-node3" ;;
        *) echo "prod-node1" ;;
    esac
}

# ─── Build Service Locally ────────────────────────
build_service() {
    local svc="$1"
    local project="${SERVICE_PROJECTS[$svc]}"
    local project_dir="$SOURCE_ROOT/$project"

    log_info "Building $svc from $project_dir..."

    if [[ ! -d "$project_dir" ]]; then
        log_error "Project directory not found: $project_dir"
        return 1
    fi

    cd "$project_dir"
    ./gradlew clean bootJar -x test --no-daemon --quiet

    local jar=$(find build/libs -name "*.jar" ! -name "*-plain.jar" | head -1)
    if [[ -z "$jar" ]]; then
        log_error "No JAR found for $svc"
        return 1
    fi

    log_ok "Built: $jar"
    echo "$project_dir/$jar"
}

# ─── Deploy Single Service via SSH ────────────────
deploy_service() {
    local svc="$1"
    local host="$2"
    local jar_path="$3"
    local port="${SERVICE_PORTS[$svc]}"
    local remote_jar="$DEPLOY_DIR/$svc/$svc.jar"

    log_info "Deploying $svc to $host..."

    # 1. Create directories on remote
    ssh "$host" "mkdir -p $DEPLOY_DIR/$svc $BACKUP_DIR $LOG_DIR"

    # 2. Backup existing JAR (if any)
    ssh "$host" "[[ -f $remote_jar ]] && cp $remote_jar $BACKUP_DIR/${svc}_prev.jar || true"

    # 3. Stop existing service gracefully
    ssh "$host" "sudo systemctl stop ecom-$svc 2>/dev/null || true"
    log_info "Stopped existing $svc instance"

    # 4. Upload new JAR via SCP
    scp "$jar_path" "$host:$remote_jar"
    log_ok "Uploaded JAR to $host:$remote_jar"

    # 5. Upload/update systemd service file
    generate_systemd_unit "$svc" "$port" | ssh "$host" "sudo tee /etc/systemd/system/ecom-$svc.service > /dev/null"
    ssh "$host" "sudo systemctl daemon-reload"

    # 6. Start service
    ssh "$host" "sudo systemctl start ecom-$svc"
    log_info "Started $svc on $host"

    # 7. Health check
    if wait_for_health "$host" "$port" "$svc"; then
        log_ok "$svc is healthy on $host:$port ✓"
        ssh "$host" "sudo systemctl enable ecom-$svc 2>/dev/null || true"
    else
        log_error "$svc failed health check on $host:$port"
        log_warn "Rolling back $svc..."
        rollback_service "$svc" "$host"
        return 1
    fi
}

# ─── Generate systemd Unit File ───────────────────
generate_systemd_unit() {
    local svc="$1"
    local port="$2"
    cat <<EOF
[Unit]
Description=E-Commerce $svc
After=network.target
Requires=network.target

[Service]
Type=simple
User=deploy
Group=deploy
WorkingDirectory=/opt/ecom/$svc
ExecStart=/usr/bin/java \\
    -Xms256m -Xmx512m \\
    -Dspring.profiles.active=${ENV} \\
    -Dserver.port=${port} \\
    -jar /opt/ecom/$svc/$svc.jar
ExecStop=/bin/kill -SIGTERM \$MAINPID
Restart=on-failure
RestartSec=10
SuccessExitStatus=143
StandardOutput=append:/opt/ecom/logs/$svc.log
StandardError=append:/opt/ecom/logs/$svc-error.log

# Resource limits
LimitNOFILE=65536
LimitNPROC=4096
MemoryMax=768M
CPUQuota=80%

# Security hardening
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/ecom/logs /opt/ecom/$svc /tmp

[Install]
WantedBy=multi-user.target
EOF
}

# ─── Health Check via SSH Tunnel ──────────────────
wait_for_health() {
    local host="$1"
    local port="$2"
    local svc="$3"
    local elapsed=0

    log_info "Waiting for $svc health check (timeout: ${HEALTH_TIMEOUT}s)..."
    while (( elapsed < HEALTH_TIMEOUT )); do
        local status
        status=$(ssh "$host" "curl -s -o /dev/null -w '%{http_code}' http://localhost:$port/actuator/health 2>/dev/null" || echo "000")
        if [[ "$status" == "200" ]]; then
            return 0
        fi
        sleep "$HEALTH_INTERVAL"
        elapsed=$((elapsed + HEALTH_INTERVAL))
    done
    return 1
}

# ─── Rollback Single Service ──────────────────────
rollback_service() {
    local svc="$1"
    local host="$2"
    local backup_jar="$BACKUP_DIR/${svc}_prev.jar"
    local remote_jar="$DEPLOY_DIR/$svc/$svc.jar"

    ssh "$host" "sudo systemctl stop ecom-$svc 2>/dev/null || true"

    if ssh "$host" "[[ -f $backup_jar ]]"; then
        ssh "$host" "cp $backup_jar $remote_jar"
        ssh "$host" "sudo systemctl start ecom-$svc"
        log_warn "Rolled back $svc to previous version"
    else
        log_error "No backup found for $svc — manual intervention required"
    fi
}

# ─── Main Execution ────────────────────────────────
main() {
    log_info "═══════════════════════════════════════"
    log_info "  E-Commerce SSH Deployment"
    log_info "  Environment: $ENV"
    log_info "  Service:     $SERVICE"
    log_info "  Timestamp:   $TIMESTAMP"
    log_info "═══════════════════════════════════════"

    local services
    if [[ "$SERVICE" == "all" ]]; then
        # Deploy in dependency order
        services=(eureka-server api-gateway user-auth product-catalog
                  inventory-service order-service payment-service notification-service)
    else
        services=("$SERVICE")
    fi

    local failed=()
    for svc in "${services[@]}"; do
        log_info "────────────────────────────────────"
        log_info "Processing: $svc"

        # Build
        local jar_path
        jar_path=$(build_service "$svc") || { failed+=("$svc"); continue; }

        # Determine target host
        local host
        if [[ "$ENV" == "prod" ]]; then
            host=$(get_service_host "$svc")
        else
            host=$(get_hosts)
        fi

        # Deploy
        deploy_service "$svc" "$host" "$jar_path" || { failed+=("$svc"); continue; }
    done

    log_info "═══════════════════════════════════════"
    if [[ ${#failed[@]} -eq 0 ]]; then
        log_ok "All services deployed successfully ✓"
    else
        log_error "Failed services: ${failed[*]}"
        exit 1
    fi
}

main
```

**Rolling Deployment (Zero-Downtime for Production):**
```bash
#!/bin/bash
set -euo pipefail

# ═══════════════════════════════════════════════════
# Rolling SSH Deployment (Production)
# Deploys one node at a time, keeping other nodes serving traffic
# ═══════════════════════════════════════════════════

PROD_NODES=("prod-node1" "prod-node2" "prod-node3")
GATEWAY_HOST="prod-node3"

for node in "${PROD_NODES[@]}"; do
    echo "══════════════════════════════════════"
    echo "Deploying to: $node"
    echo "══════════════════════════════════════"

    # 1. Drain traffic from this node
    ssh "$GATEWAY_HOST" "
        curl -s -X POST http://localhost:8761/eureka/apps/$node/status?value=OUT_OF_SERVICE
    "
    echo "[INFO] $node removed from load balancer"
    sleep 10  # Wait for in-flight requests to complete

    # 2. Deploy services on this node
    ./deploy.sh prod "$node"

    # 3. Verify all services are healthy
    services_on_node=$(ssh "$node" "systemctl list-units 'ecom-*' --no-pager --plain | awk '{print \$1}'")
    for unit in $services_on_node; do
        svc_name=$(echo "$unit" | sed 's/ecom-//' | sed 's/.service//')
        port=${SERVICE_PORTS[$svc_name]}
        if ! wait_for_health "$node" "$port" "$svc_name"; then
            echo "[ERROR] $svc_name failed on $node — halting rolling deploy"
            exit 1
        fi
    done

    # 4. Re-register node in load balancer
    ssh "$GATEWAY_HOST" "
        curl -s -X DELETE http://localhost:8761/eureka/apps/$node/status?value=UP
    "
    echo "[OK] $node re-registered in load balancer"
    sleep 15  # Wait for Eureka propagation

    echo "[OK] $node deployment complete ✓"
done

echo "════════════════════════════════════════"
echo "[OK] Rolling deployment complete ✓"
```

---

#### 7.4.5 Deployment Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│          SSH DEPLOYMENT FLOW (Single Service)                       │
└─────────────────────────────────────────────────────────────────────┘

  Developer / CI Pipeline
       │
       ▼
  ┌───────────────────┐
  │ 1. BUILD           │   ./gradlew clean bootJar -x test
  │    (Local/CI)      │   Produce: service-name.jar
  └─────────┬─────────┘
            │
            ▼
  ┌───────────────────┐
  │ 2. SSH CONNECT     │   ssh -J bastion deploy@target-host
  │    via Bastion     │   ProxyJump through hardened bastion
  └─────────┬─────────┘
            │
            ▼
  ┌───────────────────┐
  │ 3. BACKUP          │   cp current.jar → backups/timestamp/
  │    Existing JAR    │   Preserve for rollback
  └─────────┬─────────┘
            │
            ▼
  ┌───────────────────┐
  │ 4. STOP SERVICE    │   systemctl stop ecom-service
  │    Graceful        │   SIGTERM → wait for drain
  └─────────┬─────────┘
            │
            ▼
  ┌───────────────────┐
  │ 5. UPLOAD JAR      │   scp service.jar target:/opt/ecom/
  │    via SCP         │   Encrypted transfer
  └─────────┬─────────┘
            │
            ▼
  ┌───────────────────┐
  │ 6. UPDATE SYSTEMD  │   Generate & install .service unit
  │    Service Unit    │   systemctl daemon-reload
  └─────────┬─────────┘
            │
            ▼
  ┌───────────────────┐
  │ 7. START SERVICE   │   systemctl start ecom-service
  │                    │   JVM startup with Spring profiles
  └─────────┬─────────┘
            │
            ▼
  ┌───────────────────┐
  │ 8. HEALTH CHECK    │   GET /actuator/health
  │    (60s timeout)   │   Poll every 5s via SSH tunnel
  └─────────┬─────────┘
            │
       ┌────┴────┐
       │         │
    ✅ OK     ❌ FAIL
       │         │
       ▼         ▼
  ┌──────────┐ ┌──────────────┐
  │ COMPLETE │ │ ROLLBACK     │
  │ Enable   │ │ Restore prev │
  │ on boot  │ │ JAR & start  │
  └──────────┘ └──────────────┘


┌─────────────────────────────────────────────────────────────────────┐
│          SSH ROLLING DEPLOYMENT (Production - Zero Downtime)        │
└─────────────────────────────────────────────────────────────────────┘

  ┌───────────────────┐   ┌───────────────────┐   ┌───────────────────┐
  │   Node 1 (v1.0)   │   │   Node 2 (v1.0)   │   │   Node 3 (v1.0)   │
  │   ✅ SERVING       │   │   ✅ SERVING       │   │   ✅ SERVING       │
  └───────────────────┘   └───────────────────┘   └───────────────────┘
                                    │
                           STEP 1: Drain Node 1
                                    │
  ┌───────────────────┐   ┌───────────────────┐   ┌───────────────────┐
  │   Node 1          │   │   Node 2 (v1.0)   │   │   Node 3 (v1.0)   │
  │   ⏸  DRAINING     │   │   ✅ SERVING       │   │   ✅ SERVING       │
  └───────────────────┘   └───────────────────┘   └───────────────────┘
                                    │
                           STEP 2: Deploy v2.0 to Node 1
                                    │
  ┌───────────────────┐   ┌───────────────────┐   ┌───────────────────┐
  │   Node 1 (v2.0)   │   │   Node 2 (v1.0)   │   │   Node 3 (v1.0)   │
  │   🔄 DEPLOYING    │   │   ✅ SERVING       │   │   ✅ SERVING       │
  └───────────────────┘   └───────────────────┘   └───────────────────┘
                                    │
                           STEP 3: Health check → Re-register Node 1
                                    │
  ┌───────────────────┐   ┌───────────────────┐   ┌───────────────────┐
  │   Node 1 (v2.0)   │   │   Node 2 (v1.0)   │   │   Node 3 (v1.0)   │
  │   ✅ SERVING       │   │   ✅ SERVING       │   │   ✅ SERVING       │
  └───────────────────┘   └───────────────────┘   └───────────────────┘
                                    │
                           STEP 4: Repeat for Node 2, then Node 3
                                    │
  ┌───────────────────┐   ┌───────────────────┐   ┌───────────────────┐
  │   Node 1 (v2.0)   │   │   Node 2 (v2.0)   │   │   Node 3 (v2.0)   │
  │   ✅ SERVING       │   │   ✅ SERVING       │   │   ✅ SERVING       │
  └───────────────────┘   └───────────────────┘   └───────────────────┘
                                    │
                              ✅ DONE (Zero Downtime)
```

---

#### 7.4.6 CI/CD Integration (GitHub Actions)

```yaml
# .github/workflows/ssh-deploy.yml
name: SSH Deploy to Environment

on:
  push:
    branches: [main]       # Auto-deploy to dev
  workflow_dispatch:        # Manual deploy to staging/prod
    inputs:
      environment:
        description: 'Target environment'
        required: true
        type: choice
        options: [dev, staging, prod]
      service:
        description: 'Service to deploy (or "all")'
        required: true
        default: 'all'
        type: string

env:
  JAVA_VERSION: '21'
  DEPLOY_USER: 'deploy'

jobs:
  build:
    runs-on: ubuntu-latest
    outputs:
      artifact-path: ${{ steps.build.outputs.jar_path }}
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK ${{ env.JAVA_VERSION }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Build with Gradle
        id: build
        run: |
          ./gradlew clean bootJar -x test
          echo "jar_path=$(find build/libs -name '*.jar' ! -name '*-plain.jar')" >> $GITHUB_OUTPUT

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: service-jars
          path: build/libs/*.jar
          retention-days: 5

  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment: ${{ github.event.inputs.environment || 'dev' }}
    steps:
      - uses: actions/checkout@v4

      - name: Download artifact
        uses: actions/download-artifact@v4
        with:
          name: service-jars
          path: ./jars

      - name: Setup SSH key
        run: |
          mkdir -p ~/.ssh
          echo "${{ secrets.SSH_DEPLOY_KEY }}" > ~/.ssh/ecom-deploy-ed25519
          chmod 600 ~/.ssh/ecom-deploy-ed25519
          echo "${{ secrets.SSH_KNOWN_HOSTS }}" > ~/.ssh/known_hosts_ecom
          chmod 644 ~/.ssh/known_hosts_ecom

      - name: Setup SSH config
        run: |
          cat >> ~/.ssh/config << 'EOF'
          Host bastion
              HostName ${{ secrets.BASTION_HOST }}
              User deploy
              Port 2222
              IdentityFile ~/.ssh/ecom-deploy-ed25519
              StrictHostKeyChecking yes
              UserKnownHostsFile ~/.ssh/known_hosts_ecom
          EOF

      - name: Deploy via SSH
        run: |
          ENV=${{ github.event.inputs.environment || 'dev' }}
          SERVICE=${{ github.event.inputs.service || 'all' }}
          chmod +x scripts/deploy.sh
          ./scripts/deploy.sh "$ENV" "$SERVICE"

      - name: Post-deploy smoke test
        run: |
          ENV=${{ github.event.inputs.environment || 'dev' }}
          HOST="$ENV"
          ENDPOINTS=(
            "8081/actuator/health"
            "8082/actuator/health"
            "8080/actuator/health"
          )
          for ep in "${ENDPOINTS[@]}"; do
            status=$(ssh -J bastion "deploy@$HOST" "curl -s -o /dev/null -w '%{http_code}' http://localhost:$ep" || echo "000")
            if [[ "$status" != "200" ]]; then
              echo "::error::Smoke test failed for $ep (status: $status)"
              exit 1
            fi
            echo "✓ $ep healthy"
          done

      - name: Notify Slack
        if: always()
        uses: slackapi/slack-github-action@v1.25.0
        with:
          payload: |
            {
              "text": "${{ job.status == 'success' && '✅' || '❌' }} SSH Deploy to ${{ github.event.inputs.environment || 'dev' }}: ${{ job.status }}",
              "blocks": [
                {
                  "type": "section",
                  "text": {
                    "type": "mrkdwn",
                    "text": "*Deploy ${{ job.status }}*\nEnv: `${{ github.event.inputs.environment || 'dev' }}`\nService: `${{ github.event.inputs.service || 'all' }}`\nCommit: `${{ github.sha }}`"
                  }
                }
              ]
            }
        env:
          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
```

---

#### 7.4.7 SSH Deployment vs Kubernetes Comparison

| Aspect | SSH Deployment | Kubernetes |
|--------|---------------|------------|
| **Setup Complexity** | Low (SSH keys + scripts) | High (cluster setup) |
| **Infrastructure Cost** | Low (bare VMs) | Medium-High (control plane) |
| **Learning Curve** | Minimal (bash + SSH) | Steep (YAML, kubectl, Helm) |
| **Auto-Scaling** | Manual (add nodes + scripts) | Built-in (HPA) |
| **Service Discovery** | Eureka (app-level) | Built-in (kube-dns) |
| **Health Checks** | Script-based (curl) | Built-in (liveness/readiness) |
| **Rolling Updates** | Custom script | Built-in |
| **Rollback** | Script-based (backup JARs) | Built-in (kubectl rollout) |
| **Secret Management** | SSH keys + env vars / Vault | ConfigMaps + Secrets |
| **Best For** | Small teams, <10 services | Large teams, >20 services |
| **Production Readiness** | Good (with proper scripts) | Excellent (battle-tested) |

**Recommendation:** Start with SSH deployment for dev/staging environments and teams getting started. Migrate to Kubernetes when team complexity and service count justify the overhead.

---

#### 7.4.8 Monitoring Deployed Services via SSH

**Utility Scripts:**
```bash
# ─── Check all services status ─────────────────────
# Usage: ./status.sh dev|staging|prod
#!/bin/bash
ENV="${1:?Usage: status.sh <env>}"
HOSTS=$(get_hosts "$ENV")

echo "═══════════════════════════════════════════════"
echo "  Service Status: $ENV"
echo "═══════════════════════════════════════════════"
printf "%-25s %-10s %-8s %-12s\n" "SERVICE" "STATUS" "PORT" "UPTIME"
echo "───────────────────────────────────────────────"

for host in $HOSTS; do
    services=$(ssh "$host" "systemctl list-units 'ecom-*' --no-pager --plain --no-legend" 2>/dev/null)
    while IFS= read -r line; do
        unit=$(echo "$line" | awk '{print $1}')
        state=$(echo "$line" | awk '{print $4}')
        svc=$(echo "$unit" | sed 's/ecom-//' | sed 's/.service//')
        uptime=$(ssh "$host" "systemctl show $unit --property=ActiveEnterTimestamp --value" 2>/dev/null | xargs -I{} date -d {} +"%Hh %Mm" 2>/dev/null || echo "N/A")
        port=${SERVICE_PORTS[$svc]:-"????"}
        printf "%-25s %-10s %-8s %-12s\n" "$svc ($host)" "$state" "$port" "$uptime"
    done <<< "$services"
done


# ─── Tail logs across all services ─────────────────
# Usage: ./logs.sh dev service-name [lines]
#!/bin/bash
ENV="${1:?Usage: logs.sh <env> <service> [lines]}"
SVC="${2:?Specify service name}"
LINES="${3:-100}"
HOST=$(get_hosts "$ENV" | awk '{print $1}')

ssh "$HOST" "tail -n $LINES -f /opt/ecom/logs/$SVC.log"


# ─── Remote database migration via SSH tunnel ──────
# Usage: ./migrate.sh dev|staging|prod
#!/bin/bash
ENV="${1:?Usage: migrate.sh <env>}"
HOST=$(get_hosts "$ENV" | awk '{print $1}')

log_info "Running Flyway migrations on $ENV..."

# Open SSH tunnel to database
ssh -f -N -L 3307:localhost:3306 "$HOST"

# Run migrations through tunnel
flyway -url="jdbc:mysql://localhost:3307/user_db" \
       -user="$DB_USER" -password="$DB_PASS" \
       migrate

# Close tunnel
kill $(lsof -t -i:3307) 2>/dev/null || true

log_ok "Migrations complete on $ENV"
```

---

#### 7.4.9 Security Checklist for SSH Deployment

- [ ] SSH keys are Ed25519 (not RSA)
- [ ] Password authentication disabled on all servers
- [ ] Root login via SSH disabled
- [ ] Bastion host uses non-standard port (2222)
- [ ] MFA enabled on bastion host
- [ ] SSH keys rotated every 90 days
- [ ] `authorized_keys` managed via Ansible/Terraform
- [ ] SSH audit logging enabled (`LogLevel VERBOSE`)
- [ ] `MaxStartups` and `MaxAuthTries` configured
- [ ] `AllowUsers` restricts to `deploy` user only
- [ ] `ForwardAgent no` set on all connections
- [ ] Known hosts file managed and verified
- [ ] Firewall rules restrict SSH to bastion only
- [ ] CI/CD SSH key stored in GitHub Encrypted Secrets
- [ ] Failed SSH attempt alerts configured

---

## 8. Risk Assessment

### 8.1 Technical Risks

| Risk | Probability | Impact | Mitigation Strategy |
|------|-------------|--------|---------------------|
| **Service Discovery Failure** | Low | High | • Fallback to hardcoded URLs<br>• Health checks<br>• Multiple Eureka instances |
| **Kafka Message Loss** | Medium | High | • Message persistence<br>• Acknowledgment handling<br>• Dead letter queue |
| **Database Migration Issues** | Medium | Medium | • Test migrations in staging<br>• Backup before migration<br>• Rollback scripts ready |
| **Performance Degradation** | Medium | High | • Load testing before release<br>• Gradual rollout<br>• Auto-scaling configured |
| **Circuit Breaker False Positives** | Low | Medium | • Tune thresholds<br>• Monitor metrics<br>• Gradual threshold adjustments |
| **Distributed Transaction Failures** | Medium | High | • Implement saga pattern<br>• Idempotent operations<br>• Compensating transactions |
| **Cache Inconsistency** | Medium | Low | • Short TTLs<br>• Event-driven invalidation<br>• Cache warming strategy |

---

### 8.2 Operational Risks

| Risk | Probability | Impact | Mitigation Strategy |
|------|-------------|--------|---------------------|
| **Insufficient Monitoring** | Low | High | • Comprehensive observability<br>• Alert fatigue prevention<br>• Runbooks for common issues |
| **Deployment Errors** | Medium | Medium | • Automated CI/CD<br>• Deployment checklists<br>• Automated rollback triggers |
| **Team Knowledge Gap** | Medium | Medium | • Documentation<br>• Training sessions<br>• Pair programming |
| **Third-Party Service Outage** | Low | High | • Circuit breakers<br>• Fallback mechanisms<br>• SLA monitoring |
| **Cost Overruns** | Medium | Medium | • Cost monitoring<br>• Resource limits<br>• Auto-scaling bounds |

---

### 8.3 Business Risks

| Risk | Probability | Impact | Mitigation Strategy |
|------|-------------|--------|---------------------|
| **Feature Delays** | Medium | Medium | • Phased approach<br>• MVP first<br>• Regular stakeholder updates |
| **Customer Impact During Migration** | Low | High | • Blue-green deployment<br>• Feature flags<br>• Gradual rollout |
| **Incomplete Testing** | Medium | High | • Comprehensive test plan<br>• Staging environment<br>• Beta testing program |

---

## 9. Timeline & Resource Estimation

### 9.1 High-Level Timeline

```
Month 1: Foundation
├─ Week 1-2: Service Discovery & Gateway (Phase 1)
└─ Week 3-4: Notification Service (Phase 2)

Month 2: Core Features
Month 3: Core Features (continued)
├─ Week 5-7: Order Management (Phase 3)
├─ Week 8-9: Observability (Phase 4)
└─ Week 10-11: Search & Performance (Phase 5)

Month 4: Advanced Features & Deployment
├─ Week 12-14: Real-time & ML (Phase 6)
└─ Week 15-16: Cloud Deployment (Phase 7)
```

---

### 9.2 Effort Breakdown

| Phase | Tasks | Hours | Resources |
|-------|-------|-------|-----------|
| **Phase 1** | Infrastructure | 40-50 | 1 Senior Dev |
| **Phase 2** | Notifications | 35-45 | 1 Developer |
| **Phase 3** | Order Management | 70-85 | 2 Developers |
| **Phase 4** | Observability | 45-55 | 1 DevOps + 1 Dev |
| **Phase 5** | Search & Performance | 40-50 | 1 Senior Dev |
| **Phase 6** | Advanced Features | 75-90 | 2 Senior Devs |
| **Phase 7** | Cloud & SSH Deployment | 60-75 | 1 DevOps + 1 Dev |
| **Total** | All Phases | **365-455 hours** | **~3-4 months** |

---

### 9.3 Resource Requirements

**Team Composition:**
- **1 Senior Backend Developer** (Lead)
- **2 Backend Developers** (Implementation)
- **1 DevOps Engineer** (Infrastructure & Deployment)
- **1 QA Engineer** (Testing)
- **1 Product Owner** (Requirements & Prioritization)

**Infrastructure Costs (Monthly - Development):**
- AWS/GCP compute instances: $200-300
- Database services: $150-250
- Kafka cluster: $100-150
- Elasticsearch: $100-150
- Monitoring tools: $50-100
- **Total:** ~$600-950/month

**Infrastructure Costs (Monthly - Production):**
- Auto-scaling instances: $800-1200
- Database services (replicated): $500-800
- Kafka cluster (HA): $300-500
- Elasticsearch cluster: $400-600
- CDN & Load Balancer: $100-200
- Monitoring & Logging: $200-300
- **Total:** ~$2,300-3,600/month

---

### 9.4 Success Metrics

**Technical KPIs:**
- ✅ API response time p95 < 200ms
- ✅ Error rate < 0.1%
- ✅ Service availability > 99.9%
- ✅ MTTR (Mean Time To Recovery) < 15min
- ✅ Deployment frequency: Daily

**Business KPIs:**
- ✅ Order completion rate > 95%
- ✅ Payment success rate > 98%
- ✅ Email delivery rate > 99%
- ✅ Customer satisfaction score > 4.5/5

**Operational KPIs:**
- ✅ Deployment success rate > 95%
- ✅ Rollback frequency < 5%
- ✅ Alert fatigue ratio < 10%
- ✅ Documentation coverage > 90%

---

## 10. Appendices

### Appendix A: Technology Decision Matrix

| Requirement | Option 1 | Option 2 | Selected | Reason |
|-------------|----------|----------|----------|--------|
| Service Discovery | Consul | Eureka | **Eureka** | Better Spring integration |
| API Gateway | Zuul | Spring Cloud Gateway | **Spring Cloud Gateway** | Non-blocking, modern |
| Circuit Breaker | Hystrix | Resilience4j | **Resilience4j** | Active development, Java 8+ |
| Tracing | Jaeger | Zipkin | **Zipkin** | Simpler setup, good UI |
| Logging | Splunk | ELK | **ELK** | Open source, flexible |
| Metrics | Datadog | Prometheus | **Prometheus** | Open source, industry standard |
| Search | Solr | Elasticsearch | **Elasticsearch** | Better ecosystem, easier |
| Payment | Stripe | PayPal | **Stripe** | Better API, documentation |
| Email | SendGrid | AWS SES | **AWS SES** | Cost-effective, reliable |
| SMS | Twilio | AWS SNS | **Twilio** | Better developer experience |
| Deployment (VM) | Ansible | SSH Scripts | **SSH Scripts** | Simpler, fewer dependencies |
| SSH Key Type | RSA-4096 | Ed25519 | **Ed25519** | Faster, more secure, smaller keys |

---

### Appendix B: Glossary

- **Saga Pattern**: Distributed transaction pattern using compensating transactions
- **CQRS**: Command Query Responsibility Segregation
- **Circuit Breaker**: Pattern to prevent cascade failures
- **Blue-Green Deployment**: Zero-downtime deployment strategy
- **Event Sourcing**: Storing state changes as events
- **Service Mesh**: Infrastructure layer for service-to-service communication
- **Idempotent**: Operation that produces same result regardless of repetition
- **Bastion Host**: Hardened SSH gateway; single entry point to private network
- **ProxyJump**: SSH feature to hop through an intermediate host transparently
- **Ed25519**: Modern elliptic-curve SSH key algorithm (faster, more secure than RSA)
- **systemd**: Linux service manager for process lifecycle, logging, and resource limits
- **Rolling Deployment**: Updating nodes one at a time to avoid downtime

---

### Appendix C: Quick Start Commands

```bash
# Start existing services
cd e-com-user-auth && ./gradlew bootRun
cd e-com-product-catalog && ./gradlew bootRun

# Start infrastructure
docker-compose up -d

# Access services
# User Auth: http://localhost:8081
# Product Catalog: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html

# After Phase 1 implementation
# Eureka Dashboard: http://localhost:8761
# API Gateway: http://localhost:8080 (all services)

# After Phase 4 implementation
# Zipkin: http://localhost:9411
# Kibana: http://localhost:5601
# Grafana: http://localhost:3000
```

---

## 🎯 Next Steps

1. **Review this document** with your team
2. **Prioritize phases** based on business needs
3. **Setup development environment** for Phase 1
4. **Create GitHub project board** for tracking
5. **Schedule kickoff meeting** to align on approach
6. **Begin Phase 1 implementation**

---

**Document Status:** ✅ Ready for Review  
**Next Review Date:** Upon Phase 1 completion  
**Questions/Feedback:** Create GitHub issue or contact architecture team

---

*End of Document*
