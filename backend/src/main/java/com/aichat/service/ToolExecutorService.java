package com.aichat.service;

import com.aichat.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * 文件工具执行器：在 Java 后端（文件所在机器）执行代码读取工具，
 * 供 Python Agent 通过 HTTP 回调调用。含沙箱路径校验。
 */
@Service
public class ToolExecutorService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".vue", ".jsx", ".tsx",
            ".xml", ".yml", ".yaml", ".json", ".properties",
            ".md", ".txt", ".sql", ".html", ".css", ".scss",
            ".go", ".rs", ".c", ".cpp", ".h", ".hpp",
            ".kt", ".swift", ".rb", ".php", ".sh", ".toml",
            ".config", ".conf", ".ini", ".gradle", ".pom"
    );

    private static final long MAX_FILE_SIZE = 1024 * 1024;
    private static final int MAX_DIR_DEPTH = 5;
    private static final int MAX_SEARCH_RESULTS = 50;
    private static final int MAX_GLOB_RESULTS = 100;

    private static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", ".git", ".idea", "__pycache__", ".vscode",
            "target", "build", "dist", ".gradle", ".mvn"
    );

    private final WorkspaceService workspaceService;

    public ToolExecutorService(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    public String execute(String tool, Map<String, Object> args) {
        String workspace = workspaceService.getAuthorizedWorkspacePath();
        if (workspace == null || workspace.isBlank()) {
            throw new BusinessException("未授权工作区，请先在设置中授权工作区路径");
        }
        return switch (tool) {
            case "read_file" -> readFile(workspace, strArg(args, "path"));
            case "list_directory" -> listDirectory(workspace, strArg(args, "path", "."));
            case "search_code" -> searchCode(workspace, strArg(args, "pattern"), strArg(args, "path", "."));
            case "glob_files" -> globFiles(workspace, strArg(args, "pattern"), strArg(args, "path", "."));
            default -> "未知工具: " + tool;
        };
    }

    private String strArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private String strArg(Map<String, Object> args, String key, String def) {
        String v = strArg(args, key);
        return (v == null || v.isBlank()) ? def : v;
    }

    /**
     * 沙箱核心校验：返回规范化绝对路径，越界抛业务异常
     */
    private Path validatePath(String workspace, String relativePath) {
        Path workspacePath = Path.of(workspace).toAbsolutePath().normalize();
        Path target = workspacePath.resolve(relativePath).normalize();
        if (!target.startsWith(workspacePath)) {
            throw new BusinessException("路径越界：" + relativePath + " 不在授权工作区内");
        }
        return target;
    }

    private boolean isAllowedExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return ALLOWED_EXTENSIONS.contains(name.substring(dot).toLowerCase());
    }

    private String readFile(String workspace, String path) {
        if (path == null || path.isBlank()) {
            return "错误：path 参数不能为空";
        }
        try {
            Path target = validatePath(workspace, path);
            if (!Files.isRegularFile(target)) {
                return "错误：文件不存在 - " + path;
            }
            long size = Files.size(target);
            if (size > MAX_FILE_SIZE) {
                return "错误：文件过大（" + size + " bytes），最大支持 " + MAX_FILE_SIZE + " bytes";
            }
            if (!isAllowedExtension(target)) {
                return "错误：不支持的文件类型（仅允许代码/配置文件）";
            }

            List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            int width = String.valueOf(lines.size()).length();
            for (int i = 0; i < lines.size(); i++) {
                sb.append(String.format("%" + width + "d\t%s%n", i + 1, lines.get(i)));
            }
            return sb.toString();
        } catch (BusinessException e) {
            return "沙箱拦截：" + e.getMessage();
        } catch (IOException e) {
            return "读取文件异常：" + e.getMessage();
        }
    }

    private String listDirectory(String workspace, String path) {
        try {
            Path target = validatePath(workspace, path);
            if (!Files.isDirectory(target)) {
                return "错误：目录不存在 - " + path;
            }
            List<String> lines = new ArrayList<>();
            buildTree(target, "", 0, lines, workspace);
            return lines.isEmpty() ? "（空目录）" : String.join("\n", lines);
        } catch (BusinessException e) {
            return "沙箱拦截：" + e.getMessage();
        } catch (IOException e) {
            return "列目录异常：" + e.getMessage();
        }
    }

    private void buildTree(Path dir, String prefix, int depth, List<String> result, String workspace) throws IOException {
        if (depth >= MAX_DIR_DEPTH) {
            result.add(prefix + "... (最大深度 " + MAX_DIR_DEPTH + " 层)");
            return;
        }
        List<Path> entries;
        try (Stream<Path> stream = Files.list(dir)) {
            entries = stream.sorted().toList();
        }
        List<Path> filtered = entries.stream()
                .filter(p -> !IGNORED_DIRS.contains(p.getFileName().toString()))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .toList();

        for (int i = 0; i < filtered.size(); i++) {
            Path entry = filtered.get(i);
            boolean isLast = (i == filtered.size() - 1);
            String connector = isLast ? "└── " : "├── ";
            result.add(prefix + connector + entry.getFileName());

            if (Files.isDirectory(entry)) {
                String extension = isLast ? "    " : "│   ";
                buildTree(entry, prefix + extension, depth + 1, result, workspace);
            }
        }
    }

    private String searchCode(String workspace, String pattern, String path) {
        if (pattern == null || pattern.isBlank()) {
            return "错误：pattern 参数不能为空";
        }
        Pattern regex;
        try {
            regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            return "正则表达式错误：" + e.getMessage();
        }

        try {
            Path target = validatePath(workspace, path);
            if (!Files.isDirectory(target)) {
                target = target.getParent();
                if (target == null) return "错误：无效路径 - " + path;
            }

            List<String> results = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(target)) {
                for (Path file : (Iterable<Path>) stream::iterator) {
                    if (Files.isDirectory(file)) continue;
                    if (!isAllowedExtension(file)) continue;
                    if (Files.size(file) > MAX_FILE_SIZE) continue;
                    if (isIgnored(file, target)) continue;

                    String rel = workspace != null
                            ? Path.of(workspace).toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString()
                            : file.toString();
                    try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                        int[] lineNo = {1};
                        for (String line : (Iterable<String>) lines::iterator) {
                            if (regex.matcher(line).find()) {
                                results.add(rel + ":" + lineNo[0] + ": " + line.trim());
                                if (results.size() >= MAX_SEARCH_RESULTS) {
                                    results.add("... 已达最大结果数 " + MAX_SEARCH_RESULTS + "，截断");
                                    return String.join("\n", results);
                                }
                            }
                            lineNo[0]++;
                        }
                    }
                }
            }
            return results.isEmpty() ? "未找到匹配结果" : String.join("\n", results);
        } catch (BusinessException e) {
            return "沙箱拦截：" + e.getMessage();
        } catch (IOException e) {
            return "搜索代码异常：" + e.getMessage();
        }
    }

    private boolean isIgnored(Path file, Path root) {
        Path rel = root.relativize(file);
        for (Path part : rel) {
            if (IGNORED_DIRS.contains(part.toString()) || part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private String globFiles(String workspace, String pattern, String path) {
        if (pattern == null || pattern.isBlank()) {
            return "错误：pattern 参数不能为空";
        }
        try {
            Path target = validatePath(workspace, path);
            if (!Files.isDirectory(target)) {
                return "错误：目录不存在 - " + path;
            }
            Path workspacePath = Path.of(workspace).toAbsolutePath().normalize();
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

            List<String> lines = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(target)) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    Path rel = workspacePath.relativize(p.toAbsolutePath().normalize());
                    if (matcher.matches(rel)) {
                        String type = Files.isDirectory(p) ? "📁" : "📄";
                        lines.add(type + " " + rel);
                        if (lines.size() >= MAX_GLOB_RESULTS) break;
                    }
                }
            }
            if (lines.isEmpty()) {
                return "未找到匹配 " + pattern + " 的文件";
            }
            if (lines.size() >= MAX_GLOB_RESULTS) {
                lines.add("... 已达最大显示数 " + MAX_GLOB_RESULTS);
            }
            return String.join("\n", lines);
        } catch (BusinessException e) {
            return "沙箱拦截：" + e.getMessage();
        } catch (IOException e) {
            return "文件匹配异常：" + e.getMessage();
        }
    }
}
