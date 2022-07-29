# How to scratch local deployments and use Kubernetes

Typically I find that small scripts can help in deploying my microservices and infrastructure for easy-access
verification - without writing a suite of integration tests that ultimately do the same. This represents the
old model of deployment in which each resource domain is shared amongst other resources.  For example, 
the following diagram describes pre-cloud deployments of infrastructure and apps.

(http://foo)[image of this]

Now, I want to evolve to production in a kubernetes environment.  
