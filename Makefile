.PHONY: up down logs test frontend-test backend-test
up:
	docker compose up --build -d
down:
	docker compose down
logs:
	docker compose logs -f
test: frontend-test backend-test
frontend-test:
	cd frontend && npm ci && npm run lint && npm run format:check && npm test && npm run build
backend-test:
	cd backend && ./mvnw -B verify
