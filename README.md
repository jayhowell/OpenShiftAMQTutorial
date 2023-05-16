# Configuring and Using AMQ on OpenShift 4.x(4.11)

Description: This Doc will walk you through

* deploying the AMQ Operator on Openshift 4.x
* configuring the broker for different protocols
* generating self signed certs to use for your clients

### **Steps**

1. Go to Operators and chose the operator
2. Create AMQ server from Broker in the namespace/project you want it in. In this example, I've just let it be in the default project.
3. Create a self signed cert using the following commands in RHEL. When you use keytool, it will ask you for information including password. In the examples below, I've used "password" as the password. All other fields can be what ever you like.

    ```
    keytool -genkey -alias broker -keyalg RSA -keystore ./broker.ks
    keytool -export -alias broker -keystore ~/broker.ks -file ~/broker_cert.pem
    keytool -import -alias broker -keystore ~/client.ts -file ~/broker_cert.pem
    ```
4. Create the secret. Notice that we are using the broker.ks for both the client and the broker certs. This is a single phase SSO where we only use the brokers cert. Client will validate the broker cert only. If you also want the broker to also validate the client, please refer to the AMQ docs on generating a cert for the client as well.
```
    oc create secret generic amq7brokersecret --from-file=broker.ks=/home/jhowell/broker.ks --from-file=client.ts=/home/jhowell/broker.ks --from-literal=keyStorePassword=password --from-literal=trustStorePassword=password
```
  or if you already have a .crt and .key file created...        
```
    oc create secret tls amq7brokersecret --cert=</path/to/cert.crt> --key=</path/to/cert.key>
```
    
5. Link the secret to the **BROKERS** Service Account. The broker operator service account will will make sure that a volume is mounted credential in the pods under /etc/\<secret-name-from-aboe>-volume or in this case /etc/amq7brokersecret-volume
    AMQ Operaor 7.10 and below

    ```
    oc secrets link sa/amq-broker-operator secret/amq7brokersecret
    ```

    AMQ Operaor 7.11 and above

    ```
    oc secrets link sa/amq-broker-controller-manager secret/amq7brokersecret
    ```
6. Go to the operator and create the broker. When creating the broker, go to the YAML section and use this YAML

```
apiVersion: broker.amq.io/v1beta1
kind: ActiveMQArtemis
metadata:
  name: ex-aao
  namespace: amq-demo
spec:
  acceptors:
    - name: amqp
      needClientAuth: false
      port: 5672
      protocols: core, amqp
      sslEnabled: true
      sslSecret: amq7brokersecret
      verifyHost: false
      expose: true
  console:
    expose: true
  adminUser: admin
  adminPassword: password
  deploymentPlan:
    size: 2
```

* The acceptor in the spec defines how the broker will accept messages
    * configures an acceptor on port 5672
    * protocols `core` and `amqp` please see definitions in docs\<insert link here>
    * `sslEnabled` and `sslSecret` work together to define that ssl is enabled and point to the cert that defines the ssl interactin.
    * `verifyHost` is important. In the CN of your cert, you can specify a domain name like mydomain.com. If verify host is set to true, it will verify that the host has this domain name.
    * `expose:true` lets us know that it will create a route automatically for you. All routes require SSL. You can create your own route to port 61616 if you like with no ssl in it.
    * console:expose:true is important becuase this allows you to get to the admin console for the broker.
    * The Deployment Plan size of 2 tells the system to bring up 2 brokers/pods

7. After you create your Broker Please wait til both pods come up

```
oc wait --for=condition=Ready pod/ex-aao-ss-0 --timeout=1000s |oc wait --for=condition=Ready pod/ex-aao-ss-1 --timeout=1000s
```

8. List the current pods with their internal IP addresses.

```
oc get pods -o wide
```

You should see the following output

```
NAME                                             READY   STATUS    RESTARTS   AGE     IP            NODE                                        NOMINATED NODE   READINESS GATES
ex-aao-ss-0                                      1/1     Running   0          3m26s   10.131.0.12   ip-10-0-193-77.us-east-2.compute.internal   <none>           <none>
ex-aao-ss-1                                      1/1     Running   0          21s     10.131.0.13   ip-10-0-193-77.us-east-2.compute.internal   <none>           <none>
```

* Information before testing the broker.
    * The default core protocol is running on port 61616.
    * 61616 is the port that we will test the internal implementation of Artimus using the Artimus consumer and producer utilities.
    * port 61616 is meant to be an internal element and is how broker to broker communications happen.
    * There is no default route created for this internal channel.
    * There is a service called "\<broker-service-name>-hdls-svc" that is used internally and can route to any of the broker pods using a Pod S. In this case our service is called "ex-aao-hdls-svc"
    * The acceptor that we configured above is on port 5672 and automatically has an external route created for it.

9.  First lets test the builtin acceptor at port 61616 by remoting into a pod and testing it. Use one of the pod addresses we received above. We'll use pod 1 at 10.131.0.12
```
oc rsh ex-aao-ss-0
/opt/amq/bin/artemis producer --url tcp://10.131.0.12:61616 --user admin --password admin
```
* Why can't you use localhost if you're already remoted into the pod?  Becuase AMQ is not listenting on 172.0.0.1 or 0.0.0.0.

  You should see the following output.

```
Connection brokerURL = tcp://10.131.0.3:61616
Producer ActiveMQQueue[TEST], thread=0 Started to calculate elapsed time ...
Producer ActiveMQQueue[TEST], thread=0 Produced: 1000 messages
Producer ActiveMQQueue[TEST], thread=0 Elapsed time in second : 3 s
Producer ActiveMQQueue[TEST], thread=0 Elapsed time in milli second : 3410 milli seconds
```
  Check to make sure that the 1000 messages are actaully in the queue.
```
/opt/amq/bin/artemis queue stat --url tcp://10.131.0.3:61616
```

  You should get the following
```
|NAME                     |ADDRESS                  |CONSUMER_COUNT|MESSAGE_COUNT|MESSAGES_ADDED|DELIVERING_COUNT|MESSAGES_ACKED|SCHEDULED_COUNT|ROUTING_TYPE|
|$.artemis.internal.sf....|$.artemis.internal.sf....|1             |0            |0             |0               |0             |0              |MULTICAST   |
|DLQ                      |DLQ                      |0             |0            |0             |0               |0             |0              |ANYCAST     |
|ExpiryQueue              |ExpiryQueue              |0             |0            |0             |0               |0             |0              |ANYCAST     |
|TEST                     |TEST                     |0             |1000         |1000          |0               |0             |0              |ANYCAST     |
|activemq.management.6d...|activemq.management.6d...|1             |0            |0             |0               |0             |0              |MULTICAST   |
|notif.19e35e81-f422-11...|activemq.notifications   |1             |0            |11            |0               |11            |0              |MULTICAST   |
```
