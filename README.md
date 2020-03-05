# demo-chat

This application is a journey from dev to test to production.
In it, there is a core of components that make the inner
architecture for Persistence, Indexing and Messaging. From there 
we branch out into Controllers, Service Config, Cloud Discovery.

Finally, we will deploy this thing to a cloud Provider. But
Currently w
## But Why?

There are already quite a few demonstration apps that teach
us how to do things from developing a simple web server in SpringMVC
to deploying a reactive message broker client with metrics. What, however
I always find is that much like the Internet itself, most of the information
in these examples are in simplified form. 
 
What I planned to do with demo-chat is to expand on the simplification in 
typical demos, and bring out more detail to the developer looking for a means
to escape 'cargo culting'. This means the examples are quite more detailed
but do teach a lot about the underpinnings of the Spring Di, Spring Repositories, 
 Spring Service composition and Kotlin's type system among other things.

Also, I really wanted to develop a multi-user experience. A 'chat' program
was the way to get there.

# The modules, What are these modules?

There are several modules to the demo-chat application. Below is what I 
imagined them to to.  What they actually do might not be complete.
