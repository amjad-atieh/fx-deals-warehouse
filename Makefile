.PHONY: env build up down logs test coverage clean

# Target to generate standard environment variables automatically
env:
	@if [ ! -f .env ]; then \
		echo "Creating .env file..."; \
		echo "DB_USERNAME=postgres" > .env; \
		echo "DB_PASSWORD=$$(tr -dc A-Za-z0-9 </dev/urandom | head -c 20)" >> .env; \
		echo "DB_URL=jdbc:postgresql://db:5432/fxdeals" >> .env; \
		echo "Successfully created .env."; \
	else \
		echo ".env file already exists."; \
	fi

build: env
	docker-compose build --no-cache

up: env
	docker-compose up -d

down:
	docker-compose down

logs:
	docker-compose logs -f

test:
	mvn test

coverage:
	mvn clean test jacoco:report
	@echo "Coverage report: target/site/jacoco/index.html"

clean:
	docker-compose down -v
	rm -f .env
	mvn clean
