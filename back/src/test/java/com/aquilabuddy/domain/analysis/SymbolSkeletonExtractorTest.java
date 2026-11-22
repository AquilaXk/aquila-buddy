package com.aquilabuddy.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SymbolSkeletonExtractorTest {

    @Test
    void extractsJavaClassAndMethodSignaturesWhileStrippingBodiesAndSavingTokens() {
        String originalCode = """
            package com.example.service;

            import java.util.List;
            import java.util.Map;
            import java.util.HashMap;

            /**
             * Complex business logic service
             */
            public class PaymentProcessor {

                private final String apiKey;

                public PaymentProcessor(String apiKey) {
                    this.apiKey = apiKey;
                    if (apiKey == null || apiKey.isBlank()) {
                        throw new IllegalArgumentException("API key required");
                    }
                }

                @Deprecated
                public boolean processPayment(String orderId, double amount, String currency) {
                    // Lots of business validation
                    if (amount <= 0) {
                        return false;
                    }
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("orderId", orderId);
                    payload.put("amount", amount);
                    payload.put("currency", currency);
                    for (int i = 0; i < 10; i++) {
                        System.out.println("Processing step " + i);
                    }
                    return true;
                }

                public String queryStatus(String transactionId) {
                    String cleanId = transactionId.trim().toUpperCase();
                    if (cleanId.startsWith("TX_")) {
                        return "SUCCESS";
                    }
                    return "PENDING";
                }
            }
            """;

        String skeleton = SymbolSkeletonExtractor.extractSkeleton("src/main/java/PaymentProcessor.java", originalCode);

        // 핵심 심볼 및 어노테이션 보존 확인
        assertThat(skeleton).contains("public class PaymentProcessor {");
        assertThat(skeleton).contains("public PaymentProcessor(String apiKey)");
        assertThat(skeleton).contains("@Deprecated");
        assertThat(skeleton).contains("public boolean processPayment(String orderId, double amount, String currency)");
        assertThat(skeleton).contains("public String queryStatus(String transactionId)");

        // 내부 구현 본문 및 import 제거 확인
        assertThat(skeleton).doesNotContain("import java.util.List;");
        assertThat(skeleton).doesNotContain("payload.put(\"orderId\", orderId);");
        assertThat(skeleton).doesNotContain("Processing step");
        assertThat(skeleton).doesNotContain("cleanId.startsWith(\"TX_\")");

        // 70% 이상의 토큰/글자수 절감율 검증
        double reduction = 1.0 - ((double) skeleton.length() / originalCode.length());
        assertThat(reduction).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    void preservesSubsequentMethodsWhenClassContainsOneLineMethods() {
        String code = """
            package com.example.service;

            public class UserService {
                public Long getId() { return id; }
                public String getName() { return name; }
                public void processUser(String email) {
                    validate(email);
                    sendNotification(email);
                }
            }
            """;
        String skeleton = SymbolSkeletonExtractor.extractSkeleton("UserService.java", code);
        assertThat(skeleton).contains("public Long getId() { /* ... */ }");
        assertThat(skeleton).contains("public String getName() { /* ... */ }");
        assertThat(skeleton).contains("public void processUser(String email) { /* ... */ }");
        assertThat(skeleton).doesNotContain("validate(email);");
    }

    @Test
    void preservesMultilineTypeDeclaration() {
        String code = """
            public class PaymentService<T>
                extends BaseService<T>
                implements PaymentUseCase, AutoCloseable {
                public void close() {
                    cleanup();
                }
            }
            """;
        String skeleton = SymbolSkeletonExtractor.extractSkeleton("PaymentService.java", code);
        assertThat(skeleton).contains("PaymentService<T>");
        assertThat(skeleton).contains("implements PaymentUseCase, AutoCloseable {");
        assertThat(skeleton).contains("public void close() { /* ... */ }");
        assertThat(skeleton).doesNotContain("cleanup();");
    }

    @Test
    void extractsGoMethodSignaturesWithReceivers() {
        String code = """
            package server

            type HttpServer struct {
                port int
            }

            func (s *HttpServer) Start(timeout int) error {
                log.Println("starting")
                return nil
            }
            """;
        String skeleton = SymbolSkeletonExtractor.extractSkeleton("server.go", code);
        assertThat(skeleton).contains("type HttpServer struct");
        assertThat(skeleton).contains("func (s *HttpServer) Start(timeout int) error");
        assertThat(skeleton).doesNotContain("log.Println");
    }

    @Test
    void extractsPythonAsyncDefAndDecorators() {
        String code = """
            from fastapi import FastAPI

            app = FastAPI()

            @app.get("/items/{item_id}")
            async def read_item(item_id: int) -> dict:
                result = await fetch_item(item_id)
                return {"item": result}
            """;
        String skeleton = SymbolSkeletonExtractor.extractSkeleton("api.py", code);
        assertThat(skeleton).contains("@app.get(\"/items/{item_id}\")");
        assertThat(skeleton).contains("async def read_item(item_id: int) -> dict: ...");
        assertThat(skeleton).doesNotContain("fetch_item(item_id)");
    }

    @Test
    void extractsTypeScriptClassAndFunctionSignatures() {
        String tsCode = """
            import { useState } from "react";
            import type { User } from "./types";

            export class UserService {
                private users: User[] = [];

                async getUser(id: string): Promise<User> {
                    const response = await fetch("/api/users/" + id);
                    if (!response.ok) {
                        throw new Error("User not found");
                    }
                    return response.json();
                }

                saveUser(user: User): void {
                    this.users.push(user);
                    localStorage.setItem("users", JSON.stringify(this.users));
                }
            }
            """;

        String skeleton = SymbolSkeletonExtractor.extractSkeleton("src/services/UserService.ts", tsCode);

        assertThat(skeleton).contains("export class UserService {");
        assertThat(skeleton).contains("async getUser(id: string): Promise<User>");
        assertThat(skeleton).contains("saveUser(user: User): void");
        assertThat(skeleton).doesNotContain("localStorage.setItem");
        assertThat(skeleton).doesNotContain("import { useState }");
    }

    @Test
    void extractsPythonClassAndFunctionSignatures() {
        String pyCode = """
            import os
            import sys

            class TaskRunner:
                def __init__(self, name: str, concurrency: int = 4):
                    self.name = name
                    self.concurrency = concurrency
                    self._workers = []

                def run(self, task_id: str) -> bool:
                    print(f"Running task {task_id}")
                    for i in range(10):
                        print(i)
                    return True
            """;

        String skeleton = SymbolSkeletonExtractor.extractSkeleton("scripts/runner.py", pyCode);

        assertThat(skeleton).contains("class TaskRunner:");
        assertThat(skeleton).contains("def __init__(self, name: str, concurrency: int = 4): ...");
        assertThat(skeleton).contains("def run(self, task_id: str) -> bool: ...");
        assertThat(skeleton).doesNotContain("print(f\"Running task");
    }

    @Test
    void preservesShortOrBlankCode() {
        assertThat(SymbolSkeletonExtractor.extractSkeleton("App.java", "")).isEmpty();
        assertThat(SymbolSkeletonExtractor.extractSkeleton("App.java", "   ")).isEmpty();
        assertThat(SymbolSkeletonExtractor.extractSkeleton("App.java", "class App {}")).isEqualTo("class App {}");
    }

    @Test
    void countsBracesAfterEscapedBackslashTerminatedStringAndBoundsOversizedInput() {
        int packed = SymbolSkeletonExtractor.packBraceCounts("String x = \"\\\\\"; if (true) { }");
        int open = (packed >>> 16) & 0xFFFF;
        int close = packed & 0xFFFF;
        assertThat(open).isEqualTo(1);
        assertThat(close).isEqualTo(1);

        String huge = "public class Large {\n    void head() {\n        int a = 1;\n    }\n}\n"
            + "a".repeat(SymbolSkeletonExtractor.MAX_CONTENT_CHARS + 10_000);
        String skeleton = SymbolSkeletonExtractor.extractSkeleton("Large.java", huge);
        assertThat(skeleton).contains("public class Large {");
        assertThat(skeleton).contains("void head()");
    }
}
