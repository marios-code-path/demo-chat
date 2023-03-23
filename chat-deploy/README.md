# Bootstrap & Initialization

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

