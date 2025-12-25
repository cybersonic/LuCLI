<cfquery name="qPosts">
    SELECT 
        p.id,
        p.title,
        p.content,
        p.slug,
        p.status,
        p.published_at,
        p.view_count,
        u.username,
        u.first_name,
        u.last_name,
        (SELECT COUNT(*) FROM comments WHERE post_id = p.id) as comment_count
    FROM posts p
    INNER JOIN users u ON p.user_id = u.id
    WHERE p.status = 'published'
    ORDER BY p.published_at DESC
</cfquery>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LuCLI Blog Demo</title>
    <link rel="stylesheet" type="text/css" href="/assets/main.css">  
</head>
<body>
    <header>
        <div class="container">
            <h1>🚀 LuCLI Blog</h1>
            <p>Powered by Lucee CFML + MySQL in Docker</p>
        </div>
    </header>
    <cfoutput>
    <div class="container">
        <cfif qPosts.recordCount gt 0>
            <div class="stats">
                <div class="stat-item">
                    <div class="stat-number">#qPosts.recordCount#</div>
                    <div class="stat-label">Published Posts</div>
                </div>
                <div class="stat-item">
                    <div class="stat-number"><cfoutput>#arrayLen(valueList(qPosts.username).listToArray())#</cfoutput></div>
                    <div class="stat-label">Authors</div>
                </div>
                <div class="stat-item">
                    
                    <div class="stat-number">#numberFormat(ValueArray(qPosts,"view_count").sum())#</div>
                    <div class="stat-label">Total Views</div>
                </div>
            </div>
            
            <cfoutput query="qPosts">
                <article class="post">
                    <h2 class="post-title">
                        <a href="post.cfm?slug=#slug#">#title#</a>
                    </h2>
                    
                    <div class="post-meta">
                        <span>👤 #first_name# #last_name# (@#username#)</span>
                        <span>📅 #dateFormat(published_at, "mmmm d, yyyy")#</span>
                        <span>💬 #comment_count# comment<cfif comment_count neq 1>s</cfif></span>
                        <span>👁️ #numberFormat(view_count)# views</span>
                    </div>
                    
                    <div class="post-content">
                        #content#
                    </div>
                    
                    <div class="post-footer">
                        <span class="badge badge-published">#uCase(status)#</span>
                        <a href="post.cfm?slug=#slug#">Read more →</a>
                    </div>
                </article>
            </cfoutput>
        <cfelse>
            <div class="no-posts">
                <h2>No posts found</h2>
                <p>Check back later for updates!</p>
            </div>
        </cfif>
    </div>
    </cfoutput>
    <footer>
        <div class="container">
            <p>
                LuCLI Demo Blog | 
                <cfoutput>#now().year()#</cfoutput> | 
                Running on Lucee <cfoutput>#server.lucee.version#</cfoutput>
            </p>
        </div>
    </footer>
</body>
</html>
