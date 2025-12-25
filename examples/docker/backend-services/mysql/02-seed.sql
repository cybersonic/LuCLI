USE lucli_demo;

-- Insert sample users
INSERT INTO users (username, email, password_hash, first_name, last_name) VALUES
('johndoe', 'john@example.com', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890', 'John', 'Doe'),
('janedoe', 'jane@example.com', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890', 'Jane', 'Doe'),
('bobsmith', 'bob@example.com', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890', 'Bob', 'Smith'),
('alicejones', 'alice@example.com', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890', 'Alice', 'Jones'),
('charlie', 'charlie@example.com', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890', 'Charlie', 'Brown');

-- Insert sample posts
INSERT INTO posts (user_id, title, content, slug, status, published_at) VALUES
(1, 'Getting Started with LuCLI', 'LuCLI is a powerful command-line interface for Lucee CFML. In this post, we explore the basics of setting up and using LuCLI for your CFML development workflow.', 'getting-started-with-lucli', 'published', NOW() - INTERVAL 30 DAY),
(1, 'Advanced Server Management', 'Learn how to manage multiple Lucee servers with LuCLI, including environment-specific configurations and monitoring integration.', 'advanced-server-management', 'published', NOW() - INTERVAL 20 DAY),
(2, 'CFML in the Terminal', 'Discover how to write and execute CFML scripts directly from your terminal using LuCLI interactive mode.', 'cfml-in-the-terminal', 'published', NOW() - INTERVAL 15 DAY),
(3, 'Docker and CFML Development', 'A guide to setting up a complete CFML development environment using Docker containers and LuCLI.', 'docker-and-cfml-development', 'published', NOW() - INTERVAL 10 DAY),
(2, 'JMX Monitoring Best Practices', 'Deep dive into monitoring your Lucee applications with JMX and the built-in LuCLI dashboard.', 'jmx-monitoring-best-practices', 'published', NOW() - INTERVAL 5 DAY),
(4, 'Building LuCLI Modules', 'Learn how to create custom modules for LuCLI to extend its functionality.', 'building-lucli-modules', 'draft', NULL),
(5, 'Performance Tuning Tips', 'Optimize your Lucee server performance with these configuration and monitoring tips.', 'performance-tuning-tips', 'published', NOW() - INTERVAL 2 DAY);

-- Insert sample comments
INSERT INTO comments (post_id, user_id, content, parent_comment_id) VALUES
(1, 2, 'Great introduction! This helped me get started quickly.', NULL),
(1, 3, 'Thanks for this guide. Any plans for a video tutorial?', NULL),
(1, 1, 'Glad it helped! Video tutorial is in the works.', 2),
(2, 4, 'The environment configuration section is particularly useful.', NULL),
(3, 5, 'I had no idea you could do this from the terminal. Game changer!', NULL),
(3, 1, 'The interactive mode really opens up new workflows.', NULL),
(4, 2, 'Docker + CFML is such a powerful combination.', NULL),
(4, 3, 'Would love to see more examples with docker-compose.', NULL),
(5, 4, 'The dashboard is really impressive. Love the real-time metrics.', NULL),
(5, 2, 'Agreed! The memory usage graphs are especially helpful.', 9),
(7, 3, 'These tips made a huge difference in our production environment.', NULL);

-- Update view counts
UPDATE posts SET view_count = 1523 WHERE id = 1;
UPDATE posts SET view_count = 892 WHERE id = 2;
UPDATE posts SET view_count = 1105 WHERE id = 3;
UPDATE posts SET view_count = 734 WHERE id = 4;
UPDATE posts SET view_count = 456 WHERE id = 5;
UPDATE posts SET view_count = 89 WHERE id = 6;
UPDATE posts SET view_count = 312 WHERE id = 7;
