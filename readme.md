## Build

```bash
    $ mvn clean install
```

## Running in the external broker

* Copy binaries to the lib directory

```bash
    $ cp target/kapua-artemis-1.0-SNAPSHOT.jar broker/lib/
```
    
* Modify broker bootstrap.xml (all config example files are in [etc/]

```xml    
    <jaas-security domain="kapua"/>
```   
    
* Modify loging.config to use Kapua plugin

```
    kapua {
       org.eclipse.kapua.KapuaLoginModule sufficient;
    };
```    

* Add plugin to broker.xml

```xml
    <broker-plugins>
        <broker-plugin class-name="org.eclipse.kapua.KapuaPlugin" />
    </broker-plugins>
```        
        
* Run the broker    
    
```bash    
    $ cd broker/
    $ bin/artemis run
```

## Todo

* Potential Artemis improvements
    * RemotingConnectionHandler to get Remmoting connection in the login module
    * Add support for custom security manager
    * MQTT handler doesn't catch connection failure
    * Support no-jaas configuration 