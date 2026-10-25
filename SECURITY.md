# Security Policy

 SMILE takes the security seriously. Thanks for helping make SMILE safe for everyone.

## Supported Versions

We currently support security fixes for the latest stable release and selected Long-Term Support (LTS) versions for earlier Java baselines.

| Version | Java Baseline | Supported          | Status / Notes                                       |
|---------|---------------|--------------------|------------------------------------------------------|
| 7.x     | Java 25       | :white_check_mark: | Current active release line                          |
| 6.x     | Java 25       | :x:                | End-of-Life (EoL) — superseded by 7.x                |
| 5.x     | Java 25       | :x:                | End-of-Life (EoL) — superseded by 7.x                |
| 4.4.x   | Java 21       | :white_check_mark: | Supported for Java 21 LTS environments               |
| < 4.4   | Java 21       | :x:                | End-of-Life (EoL) — upgrade to 4.4.x                 |
| 3.1.x   | Java 8 / 17   | :white_check_mark: | Supported for Java 8 / 11 / 17 LTS environments      |
| < 3.1   | Java 8        | :x:                | End-of-Life (EoL) — upgrade to 3.1.x                 |

**End-of-Life (EoL) & Commercial Support Notice:** With the release of SMILE 7.x, the 5.x and 6.x release lines have reached End-of-Life (EoL) and no longer receive public security patches or maintenance updates. Organizations requiring guaranteed 24-hour Critical/High CVE resolution SLAs, security backports, or commercial IP indemnification across supported or legacy versions can obtain a commercial license or OEM maintenance subscription at [https://www.aihalo.dev/licensing.html](https://www.aihalo.dev/licensing.html) or by contacting [sales@aihalo.dev](mailto:sales@aihalo.dev).

## Reporting a Vulnerability

If you believe you have found a security vulnerability in SMILE, please report it to us through coordinated disclosure.
Please do not report security vulnerabilities through public GitHub issues, discussions, or pull requests.
SMILE repository has private vulnerability reporting enabled. Please follows the instructions:

1. On GitHub, navigate to the main page of the repository.
2. Under the repository name, click **Security**.
3. Click **Report a vulnerability** to open the advisory form.
4. Fill in the advisory details form.
5. At the bottom of the form, click **Submit report**.
6. Optionally, click **Start a temporary private fork** if you want to start to fix the issue.

Please include as much of the information listed below as you can to help us better understand and resolve the issue:

- The type of issue (e.g., buffer overflow, SQL injection, or cross-site scripting)
- Full paths of source file(s) related to the manifestation of the issue
- The location of the affected source code (tag/branch/commit or direct URL)
- Any special configuration required to reproduce the issue
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact of the issue, including how an attacker might exploit the issue

This information will help us triage your report more quickly.
