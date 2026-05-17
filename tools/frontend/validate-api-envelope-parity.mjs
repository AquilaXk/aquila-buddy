import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const apiResponseSource = readFileSync(
  join(repoRoot, "back/src/main/java/com/aquilabuddy/global/response/ApiResponse.java"),
  "utf8"
);
const exceptionHandlerSource = readFileSync(
  join(repoRoot, "back/src/main/java/com/aquilabuddy/global/exception/GlobalExceptionHandler.java"),
  "utf8"
);
const frontendApiSource = readFileSync(join(repoRoot, "front/src/lib/api.ts"), "utf8");
const packageJson = readFileSync(join(repoRoot, "front/package.json"), "utf8");
const failures = [];

function fail(message) {
  failures.push(message);
}

function assertIncludes(source, needle, message) {
  if (!source.includes(needle)) {
    fail(message);
  }
}

function assertNotIncludes(source, needle, message) {
  if (source.includes(needle)) {
    fail(message);
  }
}

assertIncludes(apiResponseSource, "boolean success,", "Backend envelope must keep the legacy success boolean.");
assertIncludes(apiResponseSource, "String status,", "Backend envelope must expose status.");
assertIncludes(apiResponseSource, 'new ApiResponse<>(true, "success", data, "success to response")', "Backend success envelope must match status/message parity.");
assertIncludes(apiResponseSource, 'new ApiResponse<>(false, "fail", null, message == null ? "fail" : message)', "Backend failure envelope must match status/message parity.");

assertIncludes(exceptionHandlerSource, "MethodArgumentNotValidException.class", "Backend validation failures must be handled by GlobalExceptionHandler.");
assertIncludes(exceptionHandlerSource, "ConstraintViolationException.class", "Backend constraint failures must be handled by GlobalExceptionHandler.");
assertIncludes(exceptionHandlerSource, "ResponseStatusException.class", "Backend response-status failures must be handled by GlobalExceptionHandler.");
assertIncludes(exceptionHandlerSource, ".status(exception.getStatusCode())", "ResponseStatusException handler must preserve HTTP status.");
assertIncludes(exceptionHandlerSource, ".body(ApiResponse.error(exception.getReason()))", "ResponseStatusException handler must return the failure envelope body.");

assertIncludes(frontendApiSource, "success?: boolean;", "Frontend API response type must accept optional success boolean.");
assertIncludes(frontendApiSource, 'status?: "success" | "fail" | string;', "Frontend API response type must accept status string.");
assertIncludes(frontendApiSource, 'payload.success === true || payload.status === "success"', "Frontend success helper must accept both response styles.");
assertIncludes(frontendApiSource, "function hasApiData", "Frontend API client must centralize data-bearing success checks.");
assertNotIncludes(frontendApiSource, "if (!payload.success", "Frontend API client must not rely on success boolean directly.");

assertIncludes(packageJson, '"validate:api-envelope"', "Frontend package scripts must expose api envelope validation.");

if (failures.length > 0) {
  console.error("API envelope parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("API envelope parity validation passed.");
