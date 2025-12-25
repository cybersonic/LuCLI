<cfparam name="url.slug" default="">

<cfif len(trim(url.slug)) eq 0>
    <cflocation url="index.cfm" addtoken="false">
</cfif>

<cfquery name="qPost">
    SELECT 
        p.id,
        p.title,
        p.content,
        p.slug,
        p.status,
        p.published_at,
        p.view_count,
        p.created_at,
        u.id as user_id,
        u.username,
        u.first_name,
        u.last_name,
        u.email
    FROM posts p
    INNER JOIN users u ON p.user_id = u.id
    WHERE p.slug = <cfqueryparam value="#url.slug#" cfsqltype="cf_sql_varchar">
    AND p.status = 'published'
</cfquery>

<cfif qPost.recordCount eq 0>
    <cflocation url="index.cfm" addtoken="false">
</cfif>

<!--- Increment view count --->
<cfquery>
    UPDATE posts 
    SET view_count = view_count + 1
    WHERE id = <cfqueryparam value="#qPost.id#" cfsqltype="cf_sql_integer">
</cfquery>

<!--- Get comments for this post --->
<cfquery name="qComments">
    SELECT 
        c.id,
        c.content,
        c.created_at,
        c.parent_comment_id,
        u.username,
        u.first_name,
        u.last_name
    FROM comments c
    INNER JOIN users u ON c.user_id = u.id
    WHERE c.post_id = <cfqueryparam value="#qPost.id#" cfsqltype="cf_sql_integer">
    AND c.is_approved = 1
    ORDER BY c.created_at ASC
</cfquery>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><cfoutput>#qPost.title# - LuCLI Blog</cfoutput></title>
   <link rel="stylesheet" type="text/css" href="/assets/main.css">
</head>
<body>


    <header>
        <div class="container">
            <h1><a href="index.cfm">🚀 LuCLI Blog</a></h1>
        </div>
    </header>
    
    <div class="container">
        <a href="index.cfm" class="back-link">← Back to all posts</a>
        
        <cfoutput>
            <article>
                <div class="post-header">
                    <h1 class="post-title">#qPost.title#</h1>
                    <div class="post-meta">
                        <span>📅 #dateFormat(qPost.published_at, "mmmm d, yyyy")# at #timeFormat(qPost.published_at, "h:mm tt")#</span>
                        <span>👁️ #numberFormat(qPost.view_count + 1)# views</span>
                        <span>💬 #qComments.recordCount# comment<cfif qComments.recordCount neq 1>s</cfif></span>
                    </div>
                </div>
                
                <div class="author-info">
                    <div class="author-avatar">
                        #left(qPost.first_name, 1)##left(qPost.last_name, 1)#
                    </div>
                    <div class="author-details">
                        <h3>#qPost.first_name# #qPost.last_name#</h3>
                        <p>@#qPost.username#</p>
                    </div>
                </div>
                
                <div class="post-content">
                    #qPost.content#
                </div>
                
                <div class="post-stats">
                    <span>Published #dateFormat(qPost.published_at, "mmmm d, yyyy")#</span>
                    <cfif dateCompare(qPost.created_at, qPost.published_at) neq 0>
                        <span>• Created #dateFormat(qPost.created_at, "mmmm d, yyyy")#</span>
                    </cfif>
                </div>
            </article>
            
            <section class="comments-section">
                <h2 class="comments-header">
                    Comments (#qComments.recordCount#)
                </h2>
                
                <cfif qComments.recordCount gt 0>
                    <cfloop query="qComments">
                        <div class="comment<cfif len(trim(parent_comment_id))> reply</cfif>">
                            <div class="comment-header">
                                <span class="comment-author">
                                    #first_name# #last_name# (@#username#)
                                </span>
                                <span class="comment-date">
                                    #dateFormat(created_at, "mmm d, yyyy")# at #timeFormat(created_at, "h:mm tt")#
                                </span>
                            </div>
                            <div class="comment-content">
                                #content#
                            </div>
                        </div>
                    </cfloop>
                <cfelse>
                    <div class="no-comments">
                        <p>No comments yet. Be the first to comment!</p>
                    </div>
                </cfif>
            </section>
        </cfoutput>
    </div>
    
    <footer>
        <div class="container">
            <p>
                <a href="index.cfm" style="color: ##7f8c8d; text-decoration: none;">← Back to all posts</a>
            </p>
        </div>
    </footer>
</body>
</html>
