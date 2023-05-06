# Root Keys

These are specific Identifiers that allow the application to establish entire scopes
such as 'Users', 'Messages', 'Topics', etc.. They are used in this case to establish
the domain representation of key entities in the application. Practical usage currently
involves permissions on domain scopes. For example, a user can be granted access to
operations on any 'user' object in the system by granting permissions for that user
to the 'user' root key. 

## Bootstrap & Initialization

The application requires a set of 'root keys' - domain and static user
specific keys to define the simplest operations. This means, the first
instance of service deployment will produce these keys, then publish them
to the configuration server. An actuator endpoint will also expose these
keys.

Here are the configurations specific to initialization.

| Property                    | detail                                  | values               |
|-----------------------------|-----------------------------------------|----------------------|
| app.users.create            | Create Initial set of users?            | Bool                 |
| app.rootkeys.create         | Create Root Keys?                       | Bool                 |
| app.rootkeys.consume.scheme | Consume for Root Keys method            | http / KV            |
| app.rootkeys.consume.source | Source URL for consuming RootKeys       | localhost:admin-port |
| app.rootkeys.publish.scheme | Publish Root Keys delivery method       | KV                   |
| app.kv.prefix               | PATH Prefix for KV                      | e.g. /config/app     |
| app.kv.rootkeys             | name of DataKey to obtain rootkeys data | e.g. 'rootkeys'      |

## Static Users

The definition of 2 static users exists within the application: Anonymous, and Admin.
Any user that is not authenticated is considered Anonymous and thus has permissions
as defined by the operator; by default, anonymous can read but not write. The Admin user is a special
user that should be granted access to all operations in the system. This user is not intended
for login, but rather for system operations.

Root Keys for both static users are created on rootkey-initialization along with other
keys.

## Helpful links for Deployments

[cloud-deployment-kubernetes](https://docs.spring.io/spring-boot/docs/2.3.x-SNAPSHOT/reference/html/deployment.html#cloud-deployment-kubernetes)
[Application Availability](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.spring-application.application-availability)