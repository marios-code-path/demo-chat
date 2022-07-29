# Secure, Traceable RSocket Messaging

This guide will discuss the strategy you might use with implementing a tracing and instrumentation on 
our RSocket services, and clients. 

# Introduction to the Technology

 .. text about dapper, zipkin, brave, etc .. Introduce the deve team behind Sleuth!

## Word Abuse

Probably the first thing you need to know about is what the heck does Span, Trace, Aspect mean?  
Lets get through some of this nomenclature to help understand what will happen in context at the application
level. If you didnt read the Getting Started Guide already, then here is a refresher.

 Span: The basic unit of work. For example, sending an RPC is a new span, as is sending a response to an RPC. Spans also have other data, such as descriptions, timestamped events, key-value annotations (tags), the ID of the span that caused them, and process IDs (normally IP addresses).  Spans can be started and stopped, and they keep track of their timing information. Once you create a span, you must stop it at some point in the future

 Trace:  A set of spans forming a tree-like structure. For example, if you run a distributed big-data store, a trace might be formed by a PUT request. 

 Aspect:

 Context:

## Versionomics

Some important data on our dependencies for this guide:

 * Spring Boot >2.3.0.RELEASE
 * Spring Cloud 3.1.3
 * Spring Cloud Sleuth 3.1.3
 
The Library includes support for Managing Spans with annotations! Per the doc, this benefits developers by:

 * API-agnostic means to collaborate with a span. Use of annotations lets users add to a span with no library dependency on a span api. Doing so lets Sleuth change its core API to create less impact to user code.
 * Reduced surface area for basic span operations. Without this feature, you must use the span api, which has lifecycle commands that could be used incorrectly. By only exposing scope, tag, and log functionality, you can collaborate without accidentally breaking span lifecycle.
 * Collaboration with runtime generated code. With libraries such as Spring Data and Feign, the implementations of interfaces are generated at runtime. Consequently, span wrapping of objects was tedious. Now you can provide annotations over interfaces and the arguments of those interfaces.

Nothing is better than reducing surface area of a changing API!

# Part 1 - But I have RSocket Services that I want to trace! (The Server Side)

## Secure TCP Connection Primer

## An RSocket Controller

## Instrumenting the RSocket Serer

# Part 3 The Clients

## Another Security (as in TCP socket) Interaction

## An RSocket Requester

## Implementing a custom client

## Instrumenting all Client Requesters

# Part 4 The Server

## Securely logging into the trace Server

## Watching trace details grow via server UI

# Acknowledgments and Tips

## Other Guides and Links

Documentation on Sleuth: https://docs.spring.io/spring-cloud-sleuth/docs/current-SNAPSHOT/reference/html/integrations.html

Added RSocket Support via: https://github.com/spring-cloud/spring-cloud-sleuth/commit/216d681b04a2fc228ca5628e4f5c4593d9b33171

Thanks to Marcingrzejszczak :https://github.com/spring-cloud/spring-cloud-sleuth/issues/1677 
