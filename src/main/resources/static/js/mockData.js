/**
 * ============================================================================
 * DỮ LIỆU MẪU DỰ PHÒNG & TRẠNG THÁI KHỞI ĐẦU (MOCK DATA)
 * ============================================================================
 * Mục đích:
 * 1. Cung cấp dữ liệu mẫu chuẩn DTO khi ứng dụng chạy ở chế độ ngoại tuyến (Offline preview).
 * 2. Giả lập cấu trúc dữ liệu của các tác vụ review, thông số diff code, và tool trace.
 * ============================================================================
 */
const MOCK_DATA = {
  currentUser: {
    username: "dev1",
    email: "dev1@test.com",
    role: "USER"
  },
  pinnedItems: [
    {
      id: "pin-1",
      title: "arne-eng-1364-add-terminal-...",
      fullTitle: "arne-eng-1364-add-terminal-execution-pipeline",
      projectId: 1,
      projectName: "soopi",
      branch: "arne-eng-1364-add-terminal-exec",
      diffAdd: 0,
      diffDel: 0,
      iconType: "pr",
      iconColor: "green",
      timeAgo: "25m",
      status: "COMPLETED",
      findingsCount: 3,
      criticalCount: 0,
      highCount: 1,
      category: "CODE_QUALITY"
    },
    {
      id: "pin-2",
      title: "feat-pasted-env-assi...",
      fullTitle: "feat-pasted-env-assignment-sanitizer",
      projectId: 1,
      projectName: "soopi",
      branch: "feat-pasted-env-sanitizer",
      diffAdd: 47,
      diffDel: 0,
      iconType: "pr",
      iconColor: "purple",
      timeAgo: "21h",
      status: "COMPLETED",
      findingsCount: 4,
      criticalCount: 1,
      highCount: 2,
      category: "SECURITY"
    },
    {
      id: "pin-3",
      title: "arne-eng-1352-add-open-in-a...",
      fullTitle: "arne-eng-1352-add-open-in-agent-action",
      projectId: 1,
      projectName: "soopi",
      branch: "arne-eng-1352-agent-action",
      diffAdd: 0,
      diffDel: 0,
      iconType: "pr",
      iconColor: "purple",
      timeAgo: "22h",
      status: "COMPLETED",
      findingsCount: 2,
      criticalCount: 0,
      highCount: 0,
      category: "PERFORMANCE"
    },
    {
      id: "pin-4",
      title: "research-gh-releases",
      fullTitle: "research-gh-releases-automation",
      projectId: 1,
      projectName: "soopi",
      branch: "research-gh-releases",
      diffAdd: 0,
      diffDel: 0,
      iconType: "none",
      iconColor: "gray",
      timeAgo: "22h",
      status: "COMPLETED",
      findingsCount: 1,
      criticalCount: 0,
      highCount: 0,
      category: "BUG"
    },
    {
      id: "pin-5",
      title: "arne-eng-1118-review-...",
      fullTitle: "arne-eng-1118-review-jwt-security-filter",
      projectId: 1,
      projectName: "soopi",
      branch: "arne-eng-1118-jwt-filter",
      diffAdd: 47,
      diffDel: 0,
      iconType: "pr",
      iconColor: "green",
      timeAgo: "1d",
      status: "COMPLETED",
      findingsCount: 5,
      criticalCount: 1,
      highCount: 2,
      category: "SECURITY"
    },
    {
      id: "pin-6",
      title: "arne-eng-1311-update-re...",
      fullTitle: "arne-eng-1311-update-repository-layer",
      projectId: 1,
      projectName: "soopi",
      branch: "arne-eng-1311-repo-layer",
      diffAdd: 32,
      diffDel: 1,
      iconType: "none",
      iconColor: "gray",
      timeAgo: "1d",
      status: "COMPLETED",
      findingsCount: 3,
      criticalCount: 0,
      highCount: 1,
      category: "PERFORMANCE"
    },
    {
      id: "pin-7",
      title: "arne-eng-1204-bring-co...",
      fullTitle: "arne-eng-1204-bring-code-review-agent-tools",
      projectId: 1,
      projectName: "soopi",
      branch: "arne-eng-1204-tools",
      diffAdd: 177,
      diffDel: 156,
      iconType: "none",
      iconColor: "gray",
      timeAgo: "8d",
      status: "COMPLETED",
      findingsCount: 8,
      criticalCount: 2,
      highCount: 3,
      category: "CODE_QUALITY"
    },
    {
      id: "pin-8",
      title: "remove-flash-icons-c...",
      fullTitle: "remove-flash-icons-cleanup-assets",
      projectId: 1,
      projectName: "soopi",
      branch: "cleanup-assets",
      diffAdd: 48,
      diffDel: 1,
      iconType: "none",
      iconColor: "gray",
      timeAgo: "11d",
      status: "COMPLETED",
      findingsCount: 1,
      criticalCount: 0,
      highCount: 0,
      category: "CODE_QUALITY"
    },
    {
      id: "pin-9",
      title: "feat-fix-notifications-f...",
      fullTitle: "feat-fix-notifications-flow-sse",
      projectId: 1,
      projectName: "soopi",
      branch: "feat-notifications-sse",
      diffAdd: 1,
      diffDel: 1,
      iconType: "none",
      iconColor: "gray",
      timeAgo: "12d",
      status: "COMPLETED",
      findingsCount: 2,
      criticalCount: 0,
      highCount: 1,
      category: "BUG"
    },
    {
      id: "pin-10",
      title: "arne-eng-1077-render-...",
      fullTitle: "arne-eng-1077-render-markdown-ast-tree",
      projectId: 1,
      projectName: "soopi",
      branch: "arne-eng-1077-markdown-ast",
      diffAdd: 641,
      diffDel: 9,
      iconType: "none",
      iconColor: "gray",
      timeAgo: "12d",
      status: "COMPLETED",
      findingsCount: 6,
      criticalCount: 1,
      highCount: 2,
      category: "PERFORMANCE"
    },
    {
      id: "pin-11",
      title: "arne-gen-1101-allow-...",
      fullTitle: "arne-gen-1101-allow-custom-gemini-prompts",
      projectId: 1,
      projectName: "soopi",
      branch: "arne-gen-1101-custom-prompts",
      diffAdd: 1000,
      diffDel: 1,
      iconType: "pr",
      iconColor: "orange",
      timeAgo: "16d",
      status: "COMPLETED",
      findingsCount: 10,
      criticalCount: 2,
      highCount: 4,
      category: "SECURITY"
    }
  ],
  projects: [
    {
      id: 1,
      name: "soopi",
      description: "AI Agent Workspace & Code Review Engine",
      path: "D:/AI Agent/aiagent/aiagent",
      language: "Java",
      ownerUsername: "dev1",
      createdAt: "2026-10-01T10:00:00",
      branches: [
        {
          id: "b-1",
          name: "floppy-squids-glow",
          diffAdd: 47,
          diffDel: 0,
          timeAgo: "2h",
          status: "COMPLETED",
          findings: [
            {
              severity: "HIGH",
              category: "SECURITY",
              file: "src/main/java/prj/anhzxje/aiagent/security/JwtAuthenticationFilter.java",
              line: 42,
              title: "Potential NullPointer when Authorization header is malformed",
              description: "The header string substring operation assumes a valid Bearer prefix without checking length, potentially causing StringIndexOutOfBoundsException.",
              suggestion: "if (bearerToken != null && bearerToken.startsWith(\"Bearer \") && bearerToken.length() > 7) { ... }"
            },
            {
              severity: "MEDIUM",
              category: "PERFORMANCE",
              file: "src/main/java/prj/anhzxje/aiagent/service/AgentTaskExecutor.java",
              line: 88,
              title: "Unbounded buffer reading in file stream tool",
              description: "Reading large files without a size threshold can cause High Memory Pressure in Java heap.",
              suggestion: "Use bounded chunk streams or FileChannel with limit of 2MB per inspect call."
            }
          ]
        },
        {
          id: "b-2",
          name: "yellow-comics-shake",
          diffAdd: 47,
          diffDel: 0,
          timeAgo: "2h",
          status: "COMPLETED",
          findings: [
            {
              severity: "LOW",
              category: "CODE_QUALITY",
              file: "src/main/java/prj/anhzxje/aiagent/dto/task/AgentTaskRequest.java",
              line: 18,
              title: "Missing Bean Validation message on @NotBlank",
              description: "Provide explicit error message string for internationalization and user feedback.",
              suggestion: "@NotBlank(message = \"Project path cannot be blank\")"
            }
          ]
        },
        {
          id: "b-3",
          name: "feat-antigravity-pr...",
          fullName: "feat-antigravity-pr-agent",
          diffAdd: 47,
          diffDel: 0,
          iconColor: "purple",
          timeAgo: "3h",
          status: "COMPLETED",
          findings: [
            {
              severity: "CRITICAL",
              category: "SECURITY",
              file: "src/main/java/prj/anhzxje/aiagent/agent/CodeReviewAgent.java",
              line: 65,
              title: "Path Traversal Risk via unchecked projectPath parameter",
              description: "The agent accepts arbitrary local paths. A malicious user could submit '../../etc/passwd' or system root directories.",
              suggestion: "Path normalizedPath = Paths.get(projectPath).normalize();\nif (!normalizedPath.startsWith(allowedBasePath)) throw new SecurityException(\"Unauthorized directory access\");"
            },
            {
              severity: "HIGH",
              category: "BUG",
              file: "src/main/java/prj/anhzxje/aiagent/service/AgentTaskService.java",
              line: 112,
              title: "Race condition on concurrent task status updates",
              description: "Task entity is updated asynchronously without optimistic locking (@Version) causing lost update anomalies.",
              suggestion: "Add @Version private Long version; to AgentTask entity."
            }
          ]
        },
        {
          id: "b-4",
          name: "general-product-hunt",
          diffAdd: 0,
          diffDel: 0,
          timeAgo: "3h",
          status: "COMPLETED",
          findings: []
        }
      ]
    },
    {
      id: 2,
      name: "shop-microservices",
      description: "E-Commerce backend with Spring Cloud & Kafka",
      path: "D:/projects/shop-microservices",
      language: "Java",
      ownerUsername: "dev1",
      createdAt: "2026-10-05T14:30:00",
      branches: [
        {
          id: "b-5",
          name: "order-service-refactor",
          diffAdd: 120,
          diffDel: 34,
          timeAgo: "5h",
          status: "COMPLETED",
          findings: [
            {
              severity: "HIGH",
              category: "SECURITY",
              file: "order-service/src/main/java/com/shop/order/PaymentClient.java",
              line: 54,
              title: "Hardcoded API secret in FeignClient",
              description: "API Key detected in source code string literal. Move to environment variable or vault secret.",
              suggestion: "@Value(\"${payment.api.key}\") private String apiKey;"
            }
          ]
        }
      ]
    }
  ],
  sampleReasoningLogs: [
    { step: 1, tool: "ListDirectoryTool", args: "{\"path\": \"src/main/java/prj/anhzxje/aiagent\"}", output: "Found 12 directories, 24 files", duration: "120ms" },
    { step: 2, tool: "GrepTool", args: "{\"query\": \"@CrossOrigin|permitAll|SecurityConfig\"}", output: "Matched 6 occurrences in SecurityConfig.java, AuthController.java", duration: "85ms" },
    { step: 3, tool: "ReadFileTool", args: "{\"filePath\": \"src/main/java/prj/anhzxje/aiagent/config/SecurityConfig.java\"}", output: "Loaded 96 lines of code", duration: "64ms" },
    { step: 4, tool: "ReadFileTool", args: "{\"filePath\": \"src/main/java/prj/anhzxje/aiagent/agent/CodeReviewAgent.java\"}", output: "Loaded 142 lines of code", duration: "92ms" },
    { step: 5, tool: "AI Reasoning", args: "Gemini 2.5 Flash High Reasoning", output: "Analysis complete. Synthesized 2 critical security issues, 3 performance bottlenecks, 1 code smell.", duration: "1420ms" }
  ]
};
