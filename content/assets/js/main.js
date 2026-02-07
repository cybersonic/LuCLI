// Theme toggle functionality
function toggleTheme() {
    const html = document.documentElement;
    const currentTheme = html.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    const toggleBtn = document.querySelector('.theme-toggle');

    html.setAttribute('data-theme', newTheme);
    toggleBtn.textContent = newTheme === 'dark' ? '☀️' : '🌙';
    localStorage.setItem('theme', newTheme);
}

function loadTheme() {
    const savedTheme = localStorage.getItem('theme') || 'dark';
    const html = document.documentElement;
    const toggleBtn = document.querySelector('.theme-toggle');

    html.setAttribute('data-theme', savedTheme);
    if (toggleBtn) {
        toggleBtn.textContent = savedTheme === 'dark' ? '☀️' : '🌙';
    }
}

// Smooth scroll for anchor links
function initSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            const target = document.querySelector(this.getAttribute('href'));
            if (target) {
                e.preventDefault();
                target.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        });
    });
}

// Active sidebar link highlighting
function updateActiveSidebarLink() {
    const sidebarLinks = document.querySelectorAll('.sidebar-link');
    const currentPath = window.location.pathname;

    sidebarLinks.forEach(link => {
        if (link.getAttribute('href') === currentPath || 
            link.getAttribute('href') === `.${currentPath}` ||
            currentPath.endsWith(link.getAttribute('href'))) {
            link.classList.add('active');
        } else {
            link.classList.remove('active');
        }
    });
}

// Table of Contents highlighting based on scroll position
function initTocHighlighting() {
    const tocLinks = document.querySelectorAll('.toc-link');
    const headings = document.querySelectorAll('.content-body h2, .content-body h3');

    if (tocLinks.length === 0 || headings.length === 0) return;

    const observer = new IntersectionObserver(
        (entries) => {
            entries.forEach(entry => {
                const id = entry.target.getAttribute('id');
                const tocLink = document.querySelector(`.toc-link[href="#${id}"]`);
                
                if (entry.isIntersecting && tocLink) {
                    tocLinks.forEach(link => link.classList.remove('active'));
                    tocLink.classList.add('active');
                }
            });
        },
        {
            rootMargin: '-80px 0px -80% 0px'
        }
    );

    headings.forEach(heading => {
        if (heading.id) {
            observer.observe(heading);
        }
    });
}

// Auto-generate table of contents if not manually created
function generateToc() {
    const tocList = document.querySelector('.toc-list');
    const contentBody = document.querySelector('.content-body');
    
    if (!tocList || !contentBody || tocList.children.length > 0) return;

    const headings = contentBody.querySelectorAll('h2, h3');
    
    headings.forEach((heading, index) => {
        // Generate ID if doesn't exist
        if (!heading.id) {
            heading.id = heading.textContent
                .toLowerCase()
                .replace(/[^a-z0-9]+/g, '-')
                .replace(/^-|-$/g, '');
        }

        const li = document.createElement('li');
        li.className = 'toc-item';
        
        const a = document.createElement('a');
        a.href = `#${heading.id}`;
        a.className = 'toc-link';
        a.textContent = heading.textContent;
        
        if (heading.tagName === 'H3') {
            a.style.paddingLeft = '0.75rem';
            a.style.fontSize = '0.8rem';
        }
        
        li.appendChild(a);
        tocList.appendChild(li);
    });
}

// Initialize everything on DOM ready
document.addEventListener('DOMContentLoaded', function() {
    loadTheme();
    initSmoothScroll();
    updateActiveSidebarLink();
    generateToc();
    initTocHighlighting();
    addSearchFunctionality();
});


function addSearchFunctionality() {
        console.log("Initializing search functionality...");
        var overlay = document.getElementById('docs-search-overlay');
        var input = document.getElementById('markspresso-search-input');
        var closeBtn = overlay ? overlay.querySelector('.docs-search-close') : null;
        var activeBefore = null;

        function openSearch() {
            if (!overlay) return;
            activeBefore = document.activeElement;
            overlay.classList.add('is-open');
            overlay.setAttribute('aria-hidden', 'false');
            if (input) {
                setTimeout(function () { input.focus(); }, 0);
            }
        }

        function closeSearch() {
            if (!overlay) return;
            overlay.classList.remove('is-open');
            overlay.setAttribute('aria-hidden', 'true');
            if (activeBefore && typeof activeBefore.focus === 'function') {
                activeBefore.focus();
            }
        }

        // Expose a hook for header search control
        window.MarkspressoOpenSearch = openSearch;

        document.addEventListener('keydown', function (e) {
            var isMac = navigator.platform && navigator.platform.toUpperCase().indexOf('MAC') >= 0;
            var metaOrCtrl = isMac ? e.metaKey : e.ctrlKey;

            // Cmd/Ctrl + K opens search
            if (metaOrCtrl && (e.key === 'k' || e.key === 'K')) {
                e.preventDefault();
                openSearch();
                return;
            }

            // Esc closes when overlay is open
            if (e.key === 'Escape' && overlay && overlay.classList.contains('is-open')) {
                e.preventDefault();
                closeSearch();
            }
        });

        if (closeBtn) {
            closeBtn.addEventListener('click', function (e) {
                e.preventDefault();
                closeSearch();
            });
        }

        if (overlay) {
            overlay.addEventListener('click', function (e) {
                if (e.target === overlay || e.target.classList.contains('docs-search-backdrop')) {
                    closeSearch();
                }
            });
        }
};