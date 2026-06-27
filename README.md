# Expense Split Application (Splitwise Backend)

A Spring Boot backend application designed to log group expenses, split bills equally among members, track who owes whom, simplify outstanding debts, and record settlements.

## Technology Stack

- **Framework**: Spring Boot (v4.0.6) / Java 17
- **Database**: MySQL
- **Security**: Spring Security & JWT (JSON Web Tokens)
- **Boilerplate Reduction**: Lombok
- **Build Tool**: Maven

## Features and Endpoints

### 1. Authentication
- `POST /auth/register` — Register a new user
- `POST /auth/login` — Authenticate user credentials and retrieve a JWT token

### 2. Group Management
- `POST /groups` — Create a new group
- `GET /groups` — Retrieve all groups
- `POST /groups/{groupId}/members` — Add a user to a group

### 3. Expenses
- `POST /expenses` — Log a group expense split equally among all members

### 4. Balances and Settlements
- `GET /groups/{groupId}/balances` — View simplified outstanding debts within a group (calculated using a greedy debt simplification algorithm)
- `POST /settlements` — Record a payment/settlement from one user to another to resolve outstanding debts

## Getting Started

### Prerequisites
- Java 17
- Maven
- MySQL server running locally or externally

### Configuration
1. Configure your database credentials and secret key in `.env` or system environment variables:
   - `DB_URL`: JDBC database URL (default: `jdbc:mysql://localhost:3306/splitwise`)
   - `DB_USERNAME`: Database username (default: `root`)
   - `DB_PASSWORD`: Database password (default: `root`)
   - `JWT_SECRET`: Secret key for signing tokens
   - `JWT_EXPIRATION`: Token expiration time in milliseconds
2. Verify setup in `src/main/resources/application.properties`.

### Running the Application
To compile and run the application locally:
```bash
./mvnw spring-boot:run
```

### Running Tests
To run the end-to-end integration tests:
```bash
./mvnw test
```
