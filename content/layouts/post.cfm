<!DOCTYPE html>
<html lang="en" data-theme="dark">
<cfoutput>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>#page.meta.title# - LuCLI Blog</title>
    <cfinclude template="partials/head.cfm">
    <link rel="stylesheet" href="/css/page.css">
</head>
<body>
    <cfinclude template="partials/nav.cfm">

    <main id="top">
        <section class="section">
            <div class="container">
                <article class="blog-post">
                    <p class="mb-3">
                        <a href="/posts/" class="text-muted">&larr; Back to Blog</a>
                    </p>
                    
                    <header class="section-header">
                        <h1 class="section-title">#page.meta.title#</h1>
                        <p class="text-muted">
                            <cfif structKeyExists(page.meta, "date") and len(page.meta.date)>
                                <time datetime="#page.meta.date#">#dateFormat(page.meta.date, "mmmm d, yyyy")#</time>
                            </cfif>
                            <cfif structKeyExists(page.meta, "author") and len(page.meta.author)>
                                &bull; #page.meta.author#
                            </cfif>
                        </p>
                    </header>

                    <div class="content-body">
                        #content#
                    </div>
                </article>
            </div>
        </section>
    </main>

    <cfinclude template="partials/footer.cfm">
</body>
</cfoutput>
</html>
