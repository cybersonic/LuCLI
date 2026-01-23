<!DOCTYPE html>
<html lang="en" data-theme="dark">
<cfoutput>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>#page.meta.title# - LuCLI</title>
    <cfinclude template="partials/head.html">
    <link rel="stylesheet" href="/css/page.css">
</head>
<body>
    <header class="header">
        <div class="container">
            <cfinclude template="partials/nav.html">
        </div>
    </header>

    <main id="top">
        <section class="section">
            <div class="container">
                <div class="section-header">
                    <h1 class="section-title">#page.meta.title#</h1>
                    <cfif structKeyExists(page, "meta") and structKeyExists(page.meta, "subtitle") and len(page.meta.subtitle)>
                        <p class="section-description">#page.meta.subtitle#</p>
                    </cfif>
                </div>

                <div class="content-body">
                    #content#
                </div>
            </div>
        </section>
    </main>

    <cfinclude template="partials/footer.cfm">
</body>
</cfoutput>
</html>
