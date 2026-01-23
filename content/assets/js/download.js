(function () {
    const REPO_API_URL = 'https://api.github.com/repos/cybersonic/LuCLI/releases/latest';

    function detectOsKey() {
        const platform = (navigator.platform || '').toLowerCase();
        const userAgent = (navigator.userAgent || '').toLowerCase();

        if (platform.includes('mac') || userAgent.includes('mac os x')) return 'macos';
        if (platform.includes('win')) return 'windows';
        if (platform.includes('linux')) return 'linux';
        return 'jar';
    }

    function applyOsDetectionOnly() {
        const osKey = detectOsKey();
        const osNames = {
            macos: 'macOS',
            linux: 'Linux',
            windows: 'Windows',
            jar: 'Any OS (JAR)'
        };

        const detectedText = document.getElementById('detected-os-text');
        if (detectedText) {
            const niceName = osNames[osKey] || 'your system';
            detectedText.textContent = 'Detected ' + niceName + ' — we\'ll recommend the best download.';
        }

        const primaryLink = document.getElementById('primary-download-link');
        const primaryLabel = document.getElementById('primary-download-label');
        const primaryDetails = document.getElementById('primary-download-details');

        if (primaryLink && primaryLabel && primaryDetails) {
            primaryLink.href = '#all-downloads';
            primaryLabel.textContent = 'Choose a download';
            primaryDetails.textContent = 'Scroll to see all available builds.';
        }
    }

    function configureDownloads(latestTag, latestVersion) {
        if (!latestTag || !latestVersion) {
            applyOsDetectionOnly();
            return;
        }

        const baseUrl = 'https://github.com/cybersonic/LuCLI/releases/download/' + latestTag + '/';

        document.querySelectorAll('[data-file-template]').forEach(link => {
            const template = link.getAttribute('data-file-template');
            if (!template) return;
            const fileName = template.replace(/__LATEST_VERSION__/g, latestVersion);
            link.href = baseUrl + fileName;
        });

        const latestReleaseLink = document.getElementById('latest-release-link');
        if (latestReleaseLink) {
            latestReleaseLink.href = 'https://github.com/cybersonic/LuCLI/releases/tag/' + latestTag;
        }

        const osKey = detectOsKey();
        const osNames = {
            macos: 'macOS',
            linux: 'Linux',
            windows: 'Windows',
            jar: 'Any OS (JAR)'
        };

        const detectedText = document.getElementById('detected-os-text');
        if (detectedText) {
            const niceName = osNames[osKey] || 'your system';
            detectedText.textContent = 'Detected ' + niceName + ' — we\'ll recommend the best download.';
        }

        const primaryLink = document.getElementById('primary-download-link');
        const primaryLabel = document.getElementById('primary-download-label');
        const primaryDetails = document.getElementById('primary-download-details');

        const matchingPerOsLink = document.querySelector('a[data-os="' + osKey + '"]');
        if (primaryLink && primaryLabel && matchingPerOsLink && matchingPerOsLink.href && !matchingPerOsLink.href.endsWith('#')) {
            primaryLink.href = matchingPerOsLink.href;
            primaryLabel.textContent = 'Download for ' + (osNames[osKey] || 'your system');
            if (primaryDetails) {
                primaryDetails.textContent = matchingPerOsLink.textContent.trim();
            }
        } else if (primaryLabel && primaryDetails) {
            primaryLink.href = '#all-downloads';
            primaryLabel.textContent = 'Choose a download';
            primaryDetails.textContent = 'Scroll to see all available builds.';
        }
    }

    function initDownloadPage() {
        // Always apply OS detection, even if GitHub is blocked
        applyOsDetectionOnly();

        if (!window.fetch) {
            // Older browsers: we can\'t safely call GitHub; just leave links as-is.
            return;
        }

        fetch(REPO_API_URL, {
            headers: {
                'Accept': 'application/vnd.github+json'
            }
        })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('GitHub API error: ' + response.status);
                }
                return response.json();
            })
            .then(function (data) {
                var tag = (data && data.tag_name) || '';
                if (!tag) {
                    applyOsDetectionOnly();
                    return;
                }

                var version = tag.replace(/^v/i, '') || tag;
                configureDownloads(tag, version);
            })
            .catch(function () {
                // Network or rate-limit issue – fall back to OS detection only
                applyOsDetectionOnly();
            });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initDownloadPage);
    } else {
        initDownloadPage();
    }
})();
