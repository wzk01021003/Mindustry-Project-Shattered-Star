/*
 * Mindustry v160.7 + 碎星计划 Mod — Windows 原生启动器
 *
 * 作用：用内置 JRE 以 -jar 启动官方 Mindustry.jar，并在首次运行时把自带的
 *       mods 目录下的 jar 安装到 %APPDATA%\Mindustry\mods\（Mindustry 标准 mod 目录）。
 * 不修改 Mindustry 任何源码，游戏行为与官方桌面版完全一致。
 *
 * 参数：
 *   --console          改用 java.exe，带控制台看日志
 *   --no-mods          跳过自动安装 Mod
 *   --reinstall-mods   强制覆盖已安装的 Mod
 *   --selftest         只写 launcher-selftest.txt 报告，不启动游戏（供 Wine/CI 验证）
 *   其余参数           原样透传给 Mindustry
 *
 * 由 x86_64-w64-mingw32-gcc 交叉编译，见 windows/build-exe.sh
 */
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#define WIN32_LEAN_AND_MEAN

#include <windows.h>
#include <wchar.h>
#include <stdio.h>
#include <stdlib.h>

#define APP_TITLE     L"Mindustry: 碎星计划 / Project Shattered Star"
#define JRE_BIN       L"jre\\bin\\"
#define GAME_JAR      L"Mindustry.jar"
#define SELFTEST_NAME L"launcher-selftest.txt"

static void notify(const wchar_t *text, UINT icon) {
    MessageBoxW(NULL, text, APP_TITLE, MB_OK | icon | MB_SETFOREGROUND);
}

static void append(wchar_t *buf, size_t cap, const wchar_t *s) {
    size_t used = wcslen(buf);
    size_t room, n;
    if (used >= cap - 1) return;
    room = cap - used - 1;
    n = wcslen(s);
    if (n > room) n = room;
    wmemcpy(buf + used, s, n);
    buf[used + n] = L'\0';
}

static int isFile(const wchar_t *p) {
    DWORD a = GetFileAttributesW(p);
    return a != INVALID_FILE_ATTRIBUTES && !(a & FILE_ATTRIBUTE_DIRECTORY);
}

/* 逐级创建目录；已存在的层级会被 CreateDirectoryW 忽略 */
static void makeDirs(wchar_t *path) {
    wchar_t *p = path;
    if (path[1] == L':' && path[2] == L'\\') p = path + 3;
    for (; *p; p++) {
        if (*p == L'\\' || *p == L'/') {
            wchar_t save = *p;
            *p = L'\0';
            CreateDirectoryW(path, NULL);
            *p = save;
        }
    }
    CreateDirectoryW(path, NULL);
}

static void writeUtf8(const wchar_t *path, const wchar_t *text) {
    int n = WideCharToMultiByte(CP_UTF8, 0, text, -1, NULL, 0, NULL, NULL);
    char *buf;
    HANDLE f;
    DWORD written;
    if (n <= 1) return;
    f = CreateFileW(path, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS,
                    FILE_ATTRIBUTE_NORMAL, NULL);
    if (f == INVALID_HANDLE_VALUE) return;
    buf = (char *)malloc((size_t)n);
    if (buf) {
        WideCharToMultiByte(CP_UTF8, 0, text, -1, buf, n, NULL, NULL);
        WriteFile(f, buf, (DWORD)(n - 1), &written, NULL);
        free(buf);
    }
    CloseHandle(f);
}

/* 把自带的 mods\*.jar 安装到 %APPDATA%\Mindustry\mods\ */
static void installMods(const wchar_t *dir, wchar_t *log, size_t cap, int force) {
    wchar_t appdata[2048];
    wchar_t dest[2048];
    wchar_t pattern[2048];
    WIN32_FIND_DATAW fd;
    HANDLE h;
    DWORD r = GetEnvironmentVariableW(L"APPDATA", appdata, 2048);

    append(log, cap, L"modsDir    = ");
    if (r == 0 || r >= 2048) {
        append(log, cap, L"(APPDATA 未设置，跳过自动安装)\r\n");
        return;
    }
    dest[0] = L'\0';
    append(dest, 2048, appdata);
    append(dest, 2048, L"\\Mindustry\\mods");
    append(log, cap, dest);
    append(log, cap, L"\r\n");
    makeDirs(dest);

    pattern[0] = L'\0';
    append(pattern, 2048, dir);
    append(pattern, 2048, L"\\mods\\*.jar");
    h = FindFirstFileW(pattern, &fd);
    if (h == INVALID_HANDLE_VALUE) {
        append(log, cap, L"  mods\\ 下没有 jar，跳过\r\n");
        return;
    }
    do {
        wchar_t src[2048], dst[2048];
        WIN32_FILE_ATTRIBUTE_DATA a;
        int need = 1;
        if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) continue;
        src[0] = L'\0';
        append(src, 2048, dir);
        append(src, 2048, L"\\mods\\");
        append(src, 2048, fd.cFileName);
        dst[0] = L'\0';
        append(dst, 2048, dest);
        append(dst, 2048, L"\\");
        append(dst, 2048, fd.cFileName);
        if (!force && GetFileAttributesExW(dst, GetFileExInfoStandard, &a)) {
            if (a.nFileSizeHigh == fd.nFileSizeHigh && a.nFileSizeLow == fd.nFileSizeLow) need = 0;
        }
        if (need) CopyFileW(src, dst, FALSE);
        append(log, cap, L"  ");
        append(log, cap, fd.cFileName);
        append(log, cap, need ? L" -> installed\r\n" : L" -> up to date\r\n");
    } while (FindNextFileW(h, &fd));
    FindClose(h);
}

int WINAPI wWinMain(HINSTANCE hInst, HINSTANCE hPrev, PWSTR lpCmdLine, int nCmdShow) {
    wchar_t self[2048], javaExe[2048], jarPath[2048], cmd[8192], report[8192];
    wchar_t *slash;
    const wchar_t *dir;
    DWORD len, r;
    int selftest, consoleMode, skipMods, forceMods;
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;

    (void)hInst; (void)hPrev; (void)nCmdShow;

    len = GetModuleFileNameW(NULL, self, 2048);
    if (len == 0 || len >= 2048) {
        notify(L"无法获取程序自身路径。", MB_ICONERROR);
        return 1;
    }
    slash = wcsrchr(self, L'\\');
    if (!slash) {
        notify(L"无法解析程序所在目录。", MB_ICONERROR);
        return 1;
    }
    *slash = L'\0';
    dir = self;

    selftest   = wcsstr(lpCmdLine, L"--selftest") != NULL;
    consoleMode = wcsstr(lpCmdLine, L"--console") != NULL;
    skipMods   = wcsstr(lpCmdLine, L"--no-mods") != NULL;
    forceMods  = wcsstr(lpCmdLine, L"--reinstall-mods") != NULL;

    /* 定位 Java 运行时：内置 JRE → SS_JAVA → JAVA_HOME → PATH */
    javaExe[0] = L'\0';
    append(javaExe, 2048, dir);
    append(javaExe, 2048, L"\\");
    append(javaExe, 2048, JRE_BIN);
    append(javaExe, 2048, consoleMode ? L"java.exe" : L"javaw.exe");
    if (!isFile(javaExe)) {
        wchar_t env[2048];
        r = GetEnvironmentVariableW(L"SS_JAVA", env, 2048);
        if (r > 0 && r < 2048 && isFile(env)) {
            javaExe[0] = L'\0';
            append(javaExe, 2048, env);
        } else {
            wchar_t jh[2048];
            r = GetEnvironmentVariableW(L"JAVA_HOME", jh, 2048);
            if (r > 0 && r < 2048) {
                wchar_t cand[2048];
                cand[0] = L'\0';
                append(cand, 2048, jh);
                append(cand, 2048, L"\\bin\\");
                append(cand, 2048, consoleMode ? L"java.exe" : L"javaw.exe");
                if (isFile(cand)) {
                    javaExe[0] = L'\0';
                    append(javaExe, 2048, cand);
                }
            }
        }
    }
    if (!isFile(javaExe)) {
        wchar_t found[2048];
        wchar_t *part = NULL;
        DWORD fr = SearchPathW(NULL, consoleMode ? L"java.exe" : L"javaw.exe",
                               NULL, 2048, found, &part);
        if (fr > 0 && fr < 2048) {
            javaExe[0] = L'\0';
            append(javaExe, 2048, found);
        }
    }
    if (!isFile(javaExe)) {
        notify(L"找不到 Java 运行时。\n\n请确认 jre\\bin\\javaw.exe 存在，"
               L"或安装 Java 17 后设置 JAVA_HOME。", MB_ICONERROR);
        return 2;
    }

    jarPath[0] = L'\0';
    append(jarPath, 2048, dir);
    append(jarPath, 2048, L"\\");
    append(jarPath, 2048, GAME_JAR);
    if (!isFile(jarPath)) {
        notify(L"找不到 Mindustry.jar。\n\n请确认它与本程序放在同一目录。", MB_ICONERROR);
        return 3;
    }

    cmd[0] = L'\0';
    append(cmd, 8192, L"\"");
    append(cmd, 8192, javaExe);
    append(cmd, 8192, L"\" -Dfile.encoding=UTF-8 -Xms256m -Xmx2g -jar \"");
    append(cmd, 8192, jarPath);
    append(cmd, 8192, L"\"");
    if (lpCmdLine && lpCmdLine[0]) {
        append(cmd, 8192, L" ");
        append(cmd, 8192, lpCmdLine);
    }

    report[0] = L'\0';
    append(report, 8192, L"[Mindustry Shattered Star launcher selftest]\r\n");
    append(report, 8192, L"exeDir    = "); append(report, 8192, dir);      append(report, 8192, L"\r\n");
    append(report, 8192, L"javaExe   = "); append(report, 8192, javaExe);  append(report, 8192, L"\r\n");
    append(report, 8192, L"gameJar   = "); append(report, 8192, jarPath);  append(report, 8192, L"\r\n");
    if (!skipMods) installMods(dir, report, 8192, forceMods);
    else append(report, 8192, L"modsDir    = (--no-mods, 跳过)\r\n");
    append(report, 8192, L"cmdline   = "); append(report, 8192, cmd);      append(report, 8192, L"\r\n");

    if (selftest) {
        wchar_t path[2048];
        path[0] = L'\0';
        append(path, 2048, dir);
        append(path, 2048, L"\\");
        append(path, 2048, SELFTEST_NAME);
        writeUtf8(path, report);
        return 0;
    }

    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));
    if (!CreateProcessW(javaExe, cmd, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
        wchar_t err[2048];
        err[0] = L'\0';
        append(err, 2048, L"启动 Java 失败（错误码 ");
        swprintf(err + wcslen(err), 2048 - wcslen(err), L"%lu", (unsigned long)GetLastError());
        append(err, 2048, L"）。\n\n请确认 jre 目录完整，或把游戏放到纯英文路径后重试。\n\n");
        append(err, 2048, cmd);
        notify(err, MB_ICONERROR);
        return 4;
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return 0;
}
