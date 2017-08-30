Talents
=======
Data Scientists who can proactively analyze data from various sources to understand how
the business performs, to build predicative models, and to build AI tools that automate
certain processes. Although the perfect candidates should be a good mix of scientist,
software engineer, and hacker, we understand that such unicorns are rare. But the bottom
line is that they should have a hard science background (math, statistics, CS, EE, physics,
etc.) and have deep understanding in machine learning. They are able to build the models
from end to end. That is, they are not only understand the advanced machine learning models
but also can do data extraction, cleaning, ad-hoc analysis, feature engineering,
visualization, training, validation, anomaly detection, etc. Besides, they should be able
to code by themselves (high level languages such as R, matlab, python, scala). Last but not
least, they are good at communication with business people to understand the problems and
enhance data collection procedures to include relevant information.
Keywords: deep learning (CNN, RNN)/neural networks, SVM, random forest, gradient boosting,
logistic regression, Gaussian process, LASSO, PCA/Kernel PCA, IsoMap, LLE, t-SNE, MDS,
K-Means, DBScan, SIB, SOM, association rule mining, hidden Markov model, conditional
random field, etc. Knowledge in NLP is big plus.

Data Architects & Engineers who build and manage the big data infrastructure and tools
that collect, store and analyze huge amount of data. This role is focused on engineering
without strong statistics and machine learning skills required. The data architects choose
optimal frameworks/solutions for ETL process, data lake, data (and/or stream) processing,
data retention. The data engineers implement, maintain, and monitor them. 
Keywords: Hadoop (HDFS and MapReduce), Hive, Impala, Spark (SparkSQL and Spark Streaming),
Flink, Kafka, Flume, NoSQL such as HBase, Cassandra, MongoDB, Lambda architecture, etc.

DevOps who are software engineers good at both coding and operations. They should have
strong foundations on computer science, algorithms and programming. They are great hackers
with OOP (C++, Java, Python) and/or functional programming (Haskell, OCaml, Scala). They are
also familiar with CI/CD process and tools such as git, Jenkins, Ansible, docker, Mesos/Kubunetes,
consul, etc.

- Experience building models in one or more languages (R, Python, SAS, STATA)
- Experience working with messy, real world data using one or more query languages (SQL, Spark, Hive)
- A degree (Advanced degree preferred but not required) in Math/Stats/Comp Sci/Engineering/Econ or similar fields
- Able to work with business users to understand the business problems and model them mathematically
- Able to work with engineering teams to implement production solutions


Centralized Logging 
===================
Logs are a critical part of any system and one of best friends of developers. Virtually every process running on a system generates logs in some form or another, which are usually written to local files. For a distributed system of dozen microservices on hundred machines, however, it is challenging to manage and access the logs. Searching for a particular error across hundreds of log files on hundreds of servers is difficult without good tools. A common approach to this problem is to setup a centralized logging solution so that multiple logs can be aggregated in a central location.

When planning a centralized log management strategy, it is important to identify business and compliance requirements, e.g. log monitoring and review processes, access control granularity, and log aggregation, alerting, reporting, and retention requirements. The log management solution should be scalable and can support new log types with new services. In general, what follows should be covered in the strategy.
-	Define log retention requirements and lifecycle policies early on, and plan to move log files to cost-efficient storage locations as soon as practical.
-	Incorporate tools and features that automate the enforcement of lifecycle policies.
-	A custom-built solution, such as an open-source ELK stack, offers flexibility but may require additional tasks, costs, and dependencies. Managed services and tools could significantly reduce operational complexity.
-	Automate the installation and configuration of log shipping agents to consistently capture system and application logs and support dynamic scaling. The agents could be included in the customized VM images.
-	Integration of both on-premises and cloud workloads.

Splunk
------
Splunk is a commercial enterprise software for searching, monitoring, and analyzing machine-generated real-time data, via a web-style interface. Splunk captures, indexes, and correlates data in a searchable repository from which it can generate graphs, reports, alerts, dashboards, and visualizations. It uses a standard API to connect directly to applications and devices. Besides log management, Splunk are also used for security, compliance, fraud detection, IoT. etc.

ELK
---
ELK stands for ElasticSearch, LogStash, and Kibana, a free open source stack for log analytics with commercial support by Elastic. Elasticsearch is a search engine based on Lucene. It provides a distributed, multitenant-capable full-text search engine with an HTTP web interface and schema-free JSON documents. Logstash is a tool to collect, process, and forward events and log messages. Collection is accomplished via configurable input plugins including raw socket/packet communication, file tailing, and several message bus clients. Once an input plugin has collected data it can be processed by any number of filters which modify and annotate the event data. Finally logstash routes events to output plugins which can forward the events to a variety of external programs including Elasticsearch, local files and several message bus implementations. Kibana is a browser-based analytics and search interface for Elasticsearch that was developed primarily to view Logstash event data.

Logstash is notoriously known for its long startup times among other things. It is also hard to debug and uses a non-standard configuration language. Recently, Elastic launched Beats as a lightweight data shipper, based on their acquisition of Packetbeat.

Splunk and the ELK stack are dominating in the log management space. Splunk has long been the market leader (10,000 customers). It is expensive and targets big enterprises. ELK, as an open source solutions, has seen adoption across all types of different companies (500,000 downloads every month). However, it is time consuming to setup and manage and the total cost of ownership (TCO) is hard to determine.

AWS CloudWatch
--------------
Amazon CloudWatch enables you to monitor your AWS resources in near real-time, including Amazon EC2 instances, Amazon EBS volumes, Elastic Load Balancers, and Amazon RDS DB instances. Metrics such as CPU utilization, latency, and request counts are provided automatically for these AWS resources. You can also supply your own logs or custom application and system metrics, such as memory usage, transaction volumes, or error rates, and Amazon CloudWatch will monitor these too.

Customers can subscribe to real-time CloudWatch Logs event feeds which they can either process themselves with Amazon Kinesis and AWS Lambda, or deliver directly to Amazon ES (Elasticsearch service) using an AWS-provided Lambda function that connects CloudWatch Logs to Amazon ES.

As a managed service, we can start using CloudWatch within minutes.

Azure Monitor
-------------
Azure Monitor is basic tool for monitoring services running on Azure that collects infrastructure-level data about the throughput of a service and the surrounding environment.

Log Analytics collects and aggregates data from many sources, though with a delay of 10 to 15 minutes. It provides a holistic IT management solution for Azure, on-premises, and third-party cloud-based infrastructure. It provides richer tools to analyze data across more sources, allows complex queries across all logs, and can proactively alert on specified conditions. We can also collect custom data into its central repository so can query and visualize it.

Git 
===
Git is a distributed revision control system it is aimed at speed, data integrity, and support for non-linear workflows. Git was created by Linus Torvalds in 2005 for development of the Linux kernel.

Basics
------
Although most other VCS system store information as a list of file-based changes, Git thinks of its data more like a set of snapshots of a mini file system. For every commit, Git stores a reference to that snapshot. If files have not changed, Git doesn’t store the file again – just a link to the previous identical file.

Every Git directory on every computer is a full-fledged repository with complete history and full version tracking abilities, independent of network access or a central server. Therefore most operations in Git only need local files and resources to operate. For example, to browse the history, Git doesn’t go out to the server to get the history. It simply reads the history directly from the local repo.

Everything in Git is check-summed by SHA-1 hash before it is stored and is then referred to by that checksum.

Common Operations
-----------------
-	`git init <directory>`

Create an empty git repo in the specified directory.

-	`git init -- bare <directory>`

Initialize an empty Git repo, but omit the working directory. Shared repo should always be created with the --bare flag.

-	`git clone <repo>`

Clone the repo to the local machine. The original repo can be located on a remote machine accessible via HTTP or SSH. Git make no distinction between the working copy and the central repo. They are all full-fledged repos.

-	`git config --global user.name <name>`
-	`git config --global user.email <email>`

Configure user info, preferences, etc.

-	`git add <file/directory>`

Adds a (new) file/directory to staging area. This doesn’t really affect the repo until commit.

-	`git commit`

Commits the staged snapshot to the project history. Note that a new file in working directory without adding to staging area won’t be committed.

-	`git stash`

Temporarily stashes changes to the working copy so that you can work on something else, and then come back and re-apply them later on. It is handy to quickly switch context while you are not ready to commit yet.

-	`git status`

Displays the state of the working directory to see what changes have been make.

-	`git log`

Displays the project history/committed snapshots.

-	`git checkout -b <branch>`

Creates a new branch with the current branch as the base. The master branch is the default branch for all repos.

-	`git merge`

Merge the specified branch into the current branch.

-	`git revert <commit>`

Undoes a commit. Note that it doesn’t really remove the commit from the project history. Instead it figures out how to undo the changes and appends a new commit.

-	`git reset`

Reset the staging area to match the most recent commit, but leave the working directory unchanged.

-	`git reset –hard`

Reset the staging area and the working directory.

-	`git remote -v`

List the remote connections to other repos. You may have multiple up streams. Use git remote add/rm/rename to add, remove, or rename remote connections, respectively.

-	`git fetch`

Imports commits from a remote repo into the local repo. The resulting commits are stored as remote branches instead of the normal local branches that we are working with.

-	`git pull`

Merges upstream changes into the local repo. git pull rolls git fetch and git merge into a single command.

-	`git push <remote> <branch>`

Push the specified branch to <remote>, along with all of the commits. This may create a local branch in the destination repo. To prevent overwriting commits, Git won’t allow the push if it results in a non-fast-forward merge in the remote repo.

-	Pull Request

Pull requests are a feature for developers to collaborate using Bitbucket or Github with a user-friendly web interface. When filing a pull request, we request that another developer (e.g. the project admin) pulls a branch from our repo into their repo. Once a pull request created, the team can review and revise the code. Finally the project admin may merge the pull request to the official repo.

Workflow
--------
There are many possible workflows with Git implementations. We would like to employ the so-called Gitflow workflow, which has been successfully used in many corporates. In this workflow, we uses a central repo as the communication hub for all developers. Each developer works locally and push branches to the central repo. The master branch stores the official release history. Meanwhile we have a develop branch as an integration branch for features. In general we should tag the commits in the master branch with a version number. A variant is that we may use the master branch for integration and maintain multiple release branches tagged with version number. This variant is more suitable for client-facing long-term-maintained software.

Each new feature should reside in its own branch, which may be pushed to the central repo for backup. The feature branches are based on the develop branch. When a feature is complete, it gets merged back to develop.

Once develop branch has accumulate sufficient features, a release branch is forked off of develop. When it is ready to ship a release, it gets merged into master and tagged with a version number. The master should be merged back to develop too. The dedicated release branch is useful for parallel multiple release development.

Hotfix branches are used to patch production releases and fork off of master. Once finished, the hotfix should be merged into both master and develop.

Bitbucket Server
----------------
Both Bitbucket Server and GitHub Enterprise are enterprise on-premise source code management tools based on Git.

With Data Center deployment option, Bitbucket Server is designed to cluster multiple active servers and increase capacity for concurrent users.  It allows instant scaling by adding new nodes.

Bitbucket integrates with JIRA for issue tracking and Confluence for wiki. Bitbucket can also integrate with other Atlassian dev tools, such as Bamboo for continuous integration and deployment.

To start with small teams (up to 10 users), Bitbucket server is very cheap (only $10 one-time payment). For 1000 users, the price is $24,000 per year.

GitHub Enterprise
-----------------
GitHub Enterprise is the on-premises version of GitHub. Its features built in issue tracking and built-in wiki. For 10 users, the price is $2,500 per year.

Docker 
====== 
Docker is an open-source project that automates the deployment of applications inside software containers. Docker provides an additional layer of abstraction and automation of operating-system-level virtualization on Linux (and recently on Windows). Docker uses the resource isolation features of the Linux kernel such as cgroups and kernel namespaces, and a union-capable file system such as OverlayFS and others to allow independent containers to run within a single Linux instance, avoiding the overhead of starting and maintaining virtual machines.

Using containers, everything required to make a piece of software run is packaged into isolated containers. Unlike VMs, containers do not bundle a full operating system - only libraries and settings required to make the software work are needed. This makes for efficient, lightweight, self-contained systems and guarantees that software will always run the same, regardless of where it’s deployed.

From the point of view of DevOps, docker solves the following problems:

-	Dependency management - the application and its dependencies are packaged into a virtual container. Dependency (e.g. python runtimes) can be packaged into a lower level image, shared by multiple applications.
-	Flexibility and portability -  runs on premises, public cloud, VMs, and bare metal, etc.
-	Binary version control - multiple versions can be installed and run simultaneously. No conflicts of jars and shared libraries.

Basics
------
An image is a lightweight, stand-alone, executable package that includes everything needed to run a piece of software, including the code, a runtime, libraries, environment variables, and configuration files.

A container is a runtime instance of an image – what the image becomes in memory when actually executed. It runs completely isolated from the host environment by default, only accessing host files and ports if configured to do so.

Containers run apps natively on the host machine’s kernel. They have better performance characteristics than virtual machines that only get virtual access to host resources through a hypervisor. Containers can get native access, each one running in a discrete process, taking no more memory than any other executable.

Fundamentals
------------
control groups (cgroups) is a Linux kernel feature that limits, accounts for, and isolates the resource usage (CPU, memory, disk I/O, network, etc.) of a collection of processes. A control group is a collection of processes that are bound by the same criteria and associated with a set of parameters or limits. These groups can be hierarchical, meaning that each group inherits limits from its parent group. The kernel provides access to multiple controllers (also called subsystems, e.g. memory, cpuacct) through the cgroup interface.

cgroups provides:
-	Resource limiting – groups can be set to not exceed a configured memory limit, which also includes the file system cache.
-	Prioritization – some groups may get a larger share of CPU utilization or disk I/O throughput.
-	Accounting – measures a group's resource usage, which may be used, for example, for billing purposes.
-	Control – freezing groups of processes, their check pointing and restarting.

Namespace isolates and virtualizes system resources of a collection of processes. The resources that can be virtualized include process IDs, hostnames, user IDs, network access, IPC, and file systems.

OverlayFS takes two directories on a single Linux host, layers one on top of the other, and provides a single unified view. These directories are often referred to as layers and the technology used to layer them is known as a union mount. Each Docker image references a list of read-only layers that represent filesystem differences. Layers are stacked on top of each other to form a base for a container’s root filesystem. The Docker storage driver is responsible for stacking these layers and providing a single unified view. When a new container is created (note this is in runtime), a new, thin, writable layer is added on top of the underlying stack. This layer is often called the “container layer”. All changes made to the running container - such as writing new files, modifying existing files, and deleting files - are written to this thin writable container layer. When the container is deleted the writable layer is also deleted. The underlying image remains unchanged.

Common Operations
-----------------

### Create an image
In a directory including a Dockerfile and all application files (binaries, configuration files, etc.), run
```
docker build -t <tag> .
```
A Dockerfile is a script which contains a collection of dockerfile commands and OS commands.  The following are some important Dockerfile commands:

#### FROM
The base image for building a new image. This command must be on top of the dockerfile.

#### RUN
Used to execute a command during the build process of the docker image.

#### ADD
Copy a file from the host machine to the new docker image. There is an option to use an URL for the file, docker will then download that file to the destination directory.

#### ENV
Define an environment variable.

#### CMD
Used for executing commands when we build a new container from the docker image.

#### ENTRYPOINT
Define the default command that will be executed when the container is running.

#### WORKDIR
This is directive for CMD command to be executed.

#### USER
Set the user or UID for the container created with the image.

#### VOLUME
Enable access/linked directory between the container and the host machine. 

### Container lifecycle

#### Run the docker container
```
docker run -it -d --name <container-name> <image-name>
```

#### Pause container
Pause the processes running inside the container.
```
docker pause <container-id/name>
```

#### Unpause container
Unpause the processes inside the container.
```
docker unpause <container-id/name>
```

#### Start container
Start the container, if present in stopped state.
```
docker start <container-id/name>
```

#### Stop container
Stop the container and processes running inside the container:
```
docker stop <container-id/name>
```
To stop all the running docker containers
```
docker stop $(docker ps -a -q)
```

#### Restart container
Restart the container as well as processes running inside the container.
```
docker restart <container-id/name>
```

#### Kill container
```
docker kill <container-id/name>
```

#### Destroy container
It is better to destroy container only if it is in stopped state.
```
docker rm <container-id/name>
```
To remove all the stopped docker containers
```
docker rm $(docker ps -q -f status=exited)
```

### Limit Resource Usage
By default, a container has no resource constraints and can use as much of a given resource as the host’s kernel scheduler will allow. There are several docker run options for us to control how much memory, CPU, or block IO a container can use.

### Logging
By default, docker logs shows the command’s STDOUT and STDERR. If your image runs a non-interactive process such as a web server or a database, that application may send its output to log files instead of STDOUT and STDERR. In this case, the official nginx image shows one workaround, and the official Apache httpd image shows another. The official nginx image creates a symbolic link from /var/log/nginx/access.log to /dev/stdout, and creates another symbolic link from /var/log/nginx/error.log to /dev/stderr, overwriting the previous devices in the process. The official httpd driver changes the httpd application’s configuration to write its normal output directly to /proc/self/fd/1 (which is STDOUT) and its errors to /proc/self/fd/2 (which is STDERR).

Docker also supports multiple logging mechanisms to help you get information from running containers and services. These mechanisms are called logging drivers. For example, syslog writes logging messages to the syslog facility, splunk writes log messages to splunk using the HTTP Event Collector. Each Docker daemon has a default logging driver, which each container uses unless you configure it to use a different logging driver.

### Runtime metrics
docker stats command live streams a container’s runtime metrics including CPU, memory usage, memory limit, and network IO metrics.

Metrics are also available from the Docker Remote API via GET /container/(id)/stats.

### Ansible
Ansible has a plugin docker_container to manage docker containers.

Continuous Integration 
======================
Continuous delivery (CD) is a software engineering approach in which teams produce software in short cycles, ensuring that the software can be reliably released at any time. It aims at building, testing, and releasing software faster and more frequently. The approach helps reduce the cost, time, and risk of delivering changes by allowing for more incremental updates to production. A straightforward and repeatable deployment process is important for continuous delivery.

One may confuses continuous delivery with continuous deployment, which means that every change is automatically deployed to production. Continuous delivery ensures that every change can be deployed to production but usually choose not to do it.

The foundation of continuous delivery is continuous integration (CI), which is a development practice that requires developers to integrate code into a shared repo several times a day. Each check-in is then verified by an automated build and test, allowing teams to detect problems early and locate them more easily.

The continuous integration pipeline should run on every commit and integration tests need to be committed together with the implementation code. For maximum benefits, test-driven development (TDD) is preferred.  The common pipeline is

-	Merge pull request to shared repo after code review
-	Optional static analysis
-	Pre-deployment test (unit test)
-	Package and deploy to DIT (Development Integration Testing) environment
-	Post-deployment testing (QA)
-	Optional stress test

Although there are many CI automation tools, Jenkins stands out due to its community. With a large number of plugins, it is capable of almost anything involved in CI. Especially the Pipeline suite of plugins lets us orchestrate automata ion. The details can be found at https://jenkins.io/doc/ and https://wiki.jenkins-ci.org/display/JENKINS/Pipeline+Plugin.

Configuration Management
========================
It is not easy to manage the configurations on a large number of machines. In fact,  configuration change is a significant cause of unintended outage. Although many admins manage systems with a collection of scripts and ad-hoc practices, we need an automation framework for a consistent, reliable and secure way to manage the environment as virtualization and cloud technology have increased the complexity and the number of systems to manage is only growing.

There are many automation tools for configuration management (CM) including CFEngine, Puppet, Chef, Ansible, etc. Although CFEngine and Puppet require clients to be installed on all servers they are supposed to manage, Ansible performs all its operations over SSH and does not require servers to have anything special, which avoids the common problem of "managing the management".

Ansible configurations, called playbooks, are simple data descriptions of the infrastructure based on YAML, which is both human-readable and machine-parsable. Playbooks can describe a policy you want your remote systems to enforce, or a set of steps in a general IT process. At a basic level, playbooks can be used to manage configurations of and deployments to remote machines. At a more advanced level, they can sequence multi-tier rollouts involving rolling updates, and can delegate actions to other hosts, interacting with monitoring servers and load balancers along the way.

Each playbook is composed of one or more ‘plays’ in a list. A play selects a particular fine-grained group of hosts and assigns some tasks (or “roles”) for them to execute. Tasks are typically declarations that a particular resource be in a particular state, such that a package be installed at a specific version or that a source control repository be checked out at a particular version.

Ansible features an state-driven resource model that describes the desired state of computer systems and services, not the paths to get them to this state. No matter what state a system is in, Ansible understands how to transform it to the desired state (and also supports a "dry run" mode to preview needed changes). This allows reliable and repeatable IT infrastructure configuration, avoiding the potential failures from scripting and script-based solutions that describe explicit and often irreversible actions rather than the end goal.

In addition to CD of production services, it’s also possible to continuously deploy Ansible Playbook content itself. This allows systems administrators and developers to commit their Ansible Playbook code to a central repository, run some tests against a stage environment, and automatically apply those configurations to production upon passing stage. This allows the same software process used for deploying software to be applied to configuration management, reaping many of the same benefits.

The handoff for CD from CI is that Jenkins can invoke Ansible upon a successful build.

Service Discovery 
================= 
In a cloud based microservices application, service discovery is a difficult problem because the set of instances, and their IP addresses, are subject to constant change because of autoscaling, failures, and upgrades. Consequently, we need to use a more elaborate service discovery mechanism.

Client side Discovery
---------------------
In this pattern, the client is responsible for determining the network locations of available service instances and load balancing requests across them. The client queries a service registry, which is a database of available service instances. The client then uses a load balancing algorithm to select one of the available service instances and makes a request.

For example, Netflix Eureka is a service registry that provides a REST API for managing service instance registration and for querying available instances. Netflix Ribbon is a client-side IPC library that works with Eureka to load balance requests across the available service instances.

This straightforward and flexible approach allows the client to make intelligent, application specific load balancing decisions as the client knows about the available services instances. However, the client has to implement the service discovery logic (possibly in multiple programming languages).

Server side Discovery
---------------------
With this pattern, the load balancer queries a service registry about service locations and clients interact only with the load balancer. That is, the client makes a request to a service via a load balancer. The load balancer queries the service registry and routes each request to an available service instance.

The AWS Elastic Load Balancer (ELB) is such an example. A client makes requests (HTTP or TCP) via the ELB and the ELB load balances the traffic among a set of registered services. There isn’t a separate service registry as services are registered with the ELB itself.

This approach abstracts the details of discovery from the client. Some deployment environments provide this functionality for free. On the other hand, the load balancer needs to setup and manage unless the deployment environment provides it. Besides, the default load balance policy (round-robin, least connections, least time, generic hash, and IP hash) may not be suitable to all applications. For example, NGINX Plus is a popular choice for load balancer that supports session persistence, health checks, live activity monitoring, and on-the-fly reconfiguration of load-balanced server groups.

Service Registry
----------------
The service registry is a database populated with information on how to dispatch requests to microservice instances. Whenever a service endpoint changes, the registry needs to know about the change. The network location of a service instance is registered with the service registry when it starts up. It is removed from the service registry when the instance terminates. The service instance’s registration is typically refreshed periodically using a heartbeat mechanism.

### ZooKeeper
ZooKeeper is a centralized service for maintaining configuration information, naming, providing distributed synchronization, and providing group services. Essentially, ZooKeeper is a distributed in-memory CP data store. The data is organized in a tree structure like the file system. Higher order constructs, e.g. barriers, message queues, locks, two-phase commit, and leader election, can also be implemented with ZooKeeper.
Many Hadoop services, DC/OS, NoSQL databases depends on ZooKeeper. However, ZooKeeper is complex. To simplify the programming and management, we should employ Curator and Exhibitor. The Curator Framework is a high-level API that greatly simplifies using ZooKeeper. It adds many features that build on ZooKeeper and handles the complexity of managing connections to the ZooKeeper cluster and retrying operations. Exhibitor is a Java supervisor system for ZooKeeper. For example, it watches a ZK instance and makes sure it is running; performs periodic backups and cleaning of ZK log directory. It also have a GUI explorer for viewing ZK nodes and a rich REST API.
A canonical usage of ZooKeeper is to maintain a service registry with ephemeral nodes. When a service creates a node, it can create it as ephemeral. As long as it is alive (based on heartbeats, part of ZooKeeper protocol), the node is kept alive. If the Zookeeper server ever loses the heartbeat, it will delete the relevant node.

### etcd
etcd is a distributed reliable key-value store, written in Go and use the Raft  consensus algorithm to manage a highly-available replicated log. It is very easy to deploy, setup and use. Kubernetes uses etcd for persistent storage of all of its REST API objects. For service discovery, etcd needs to be combined with a few third-party tools such as Registrator and confd, which we will discuss below.

### Consul
Although ZooKeeper and etcd can be used for service discovery (combined with other tools), Consul is designed for discovering and configuring services. Like ZooKeeper and etcd, Consul provides a hierarchical key/value store, which requires a quorum to operate and provide strong consistency. In addition, Consul has native support for multiple datacenters as well as a more feature-rich gossip system that links server nodes and clients. Consul uses a very different architecture for health checking. Instead of simple heartbeats, Consul clients run on every node in the cluster and thus enable a much richer set of health checks to be run locally. These clients are part of a gossip pool which implements an efficient failure detector that can scale to clusters of any size without concentrating the work on any select group of servers. By using client nodes, Consul provides a simple API that only requires thin clients. Additionally, the API can be avoided entirely by using configuration files and the DNS interface to have a complete service discovery solution with no development at all.

Consul Template uses Consul to update files and execute commands when it detects the services in Consul have changed. For example, it can rewrite an nginx.conf file to include all the routing information of the services then reload the nginx configuration to load-balance many similar services or provide a single end-point to multiple services.

### Registrator
Registrator automatically registers and deregisters services by inspecting docker containers as they are brought online or stopped. It currently supports etcd, Consul and SkyDNS 2.

Registrator is run on every host and the primary argument when starting Registrator is a registry URI (Consul in the below example):
```
$ docker run -d --name=registrator --net=host --volume=/var/run/docker.sock:/tmp/docker.sock gliderlabs/registrator:latest consul://localhost:8500
```
Here we run the container detached and name it. We also run in host network mode, which makes sure Registrator has the hostname and IP of the actual host. We also must mount the Docker socket so that we can monitor the lifecycle of containers.

### confd
Similar to Consul Template, confd is a lightweight tool that can be used to maintain configuration files. The most common usage of the tool is keeping configuration files up-to-date using data stored in etcd, consul, and few other data registries. It can also be used to reload applications when configuration files change. In other words, we can use it as a way to reconfigure services with the information stored in ZooKeeper, etcd, or Consul, etc.

Cluster Resource Management
===========================
YARN
----
Originally, Hadoop was restricted mainly to the paradigm MapReduce, where the resource management is done by JobTracker and TaskTacker. The JobTracker farms out MapReduce tasks to specific nodes in the cluster, ideally the nodes that have the data, or at least are in the same rack. A TaskTracker is a node in the cluster that accepts tasks - Map, Reduce and Shuffle operations - from a JobTracker. From v2.0, Hadoop architecturally decouples the resource management features from the programming model of MapReduce, which makes Hadoop clusters more generic. The new resource manager is referred to as YARN. Now MapReduce is one kind of applications running in a YARN container and other types of applications can be written generically to run on YARN.

In comparison, HUGS is like MapReduce v1.0 that tightly couples resource management and programming model (even programming language) together. Even worse, HUGS doesn't optimize data locality as the compute Grid is separated from storage cluster. 

YARN employs a master-slave model and includes several components:
-	The global Resource Manager is the ultimate authority that arbitrates resources among all applications in the system.
-	The per-application Application Master negotiates resources from the Resource Manager and works with the Node Managers to execute and monitor the component tasks.
-	The per-node slave Node Manager is responsible for launching the applications’ containers, monitoring their resource usage and reporting to the Resource Manager.

![YARN Architecture](http://haifengl.github.io/bigdata/images/yarn-architecture.png)

The Resource Manager, consisting of Scheduler and Application Manager, is the central authority that arbitrates resources among various competing applications in the cluster. The Scheduler is responsible for allocating resources to the various running applications subject to the constraints of capacities, queues etc. The Application Manager is responsible for accepting job-submissions, negotiating the first container for executing the application specific Application Master and provides the service for restarting the Application Master container on failure.

The Scheduler uses the abstract notion of a Resource Container which incorporates elements such as memory, CPU, disk, network etc. Initially, YARN uses the memory-based scheduling. Each node is configured with a set amount of memory and applications request containers for their tasks with configurable amounts of memory. Now YARN has CPU as a resource in the same manner. Nodes are configured with a number of “virtual cores” (vcores) and applications give a vcore number in the container request.

The Scheduler has a pluggable policy plug-in, which is responsible for partitioning the cluster resources among the various queues, applications etc. For example, the Capacity Scheduler is designed to maximize the throughput and the utilization of shared, multi-tenant clusters. Queues are the primary abstraction in the Capacity Scheduler. The capacity of each queue specifies the percentage of cluster resources that are available for applications submitted to the queue. Furthermore, queues can be set up in a hierarchy. YARN also sports a Fair Scheduler that tries to assign resources to applications such that all applications get an equal share of resources over time on average using dominant resource fairness.

The protocol between YARN and applications is as follows. First an Application Submission Client communicates with the Resource Manager to acquire a new Application Id. Then it submits the Application to be run by providing sufficient information (e.g. the local files/jars, command line, environment settings, etc.) to the Resource Manager to launch the Application Master. The Application Master is then expected to register itself with the Resource Manager and request for and receive containers. After a container is allocated to it, the Application Master communicates with the Node Manager to launch the container for its task by specifying the launch information such as command line specification, environment, etc. The Application Master also handles failures of job containers. Once the task is completed, the Application Master signals the Resource Manager.
As the central authority of the YARN cluster, the Resource Manager is also the single point of failure (SPOF). To make it fault tolerant, an Active/Standby architecture can be employed since Hadoop 2.4. Multiple Resource Manager instances (listed in the configuration file yarn-site.xml) can be brought up but only one instance is Active at any point of time while others are in Standby mode. When the Active goes down or becomes unresponsive, another Resource Manager is automatically elected by a ZooKeeper-based method to be the Active. Clients, Application Masters and Node Managers try connecting to the Resource Managers in a round-robin fashion until they hit the new Active.

Mesos
-----
Mesos uses the master-slave architecture similar to YARN but with a very different design goal. Mesos aims to enable sharing clusters between multiple diverse cluster computing frameworks, such as Hadoop, Spark and MPI. This improves cluster utilization and avoids per-framework data replication.

![Mesos Architecture](http://mesos.apache.org/assets/img/documentation/architecture3.jpg)

Although a major goal of Mesos is to support various distributed computing paradigm, it admits that it doesn't know all the scheduling characteristics of each framework, especially in a customer environment such as the Bank. Therefore, Mesos introduces a distributed two-level scheduling mechanism and delegates control over scheduling to the frameworks. This allows frameworks to implement diverse approaches to various problems in the cluster and to evolve these solutions independently.

Mesos consists of a master process that manages slave daemons running on each cluster node, and frameworks that run tasks on these slaves. The master implements resource sharing using through the abstraction resource offer, which encapsulates a bundle of resources that a framework can allocate on a cluster node to run tasks. The master decides how many resources to offer to each framework according to some (pluggable) policy.

A framework running on top of Mesos consists of two components: a scheduler that registers with the master to be offered resources, and an executor process that is launched on slave nodes to run the framework's tasks. Resource offers are simple and efficient to implement, allowing Mesos to be highly scalable and robust to failures. However, how can the constraints of a framework be satisfied without Mesos knowing about these constraints? For example, how can a framework achieve data locality without Mesos knowing which nodes store the data required by the framework? A common practice is to let a framework reject the offers that do not satisfy its constraints and accept the ones that do.

###DC/OS
DC/OS stands for DataCenter Operating System including a cluster manager (i.e. Mesos), container orchestration, and common OS services. Note that DC/OS is not a host OS. DC/OS spans multiple machines, but relies on each machine to have its own host operating system and host kernel.

All tasks on DC/OS are containerized. Containers can be started from images downloaded from a container repository (e.g. Docker Hub) or they can be native executables (e.g. binaries or scripts) containerized at runtime. For container orchestration, DC/OS includes two built-in task schedulers (Marathon and DC/OS Jobs (Metronome, currently not scalable to very large batches)) and two container runtimes (Docker and Mesos). Since it is based on Mesos, we may add custom schedulers (e.g. schedulers for stateful services like databases and message queues).

On top of cluster management and container orchestration functionality, DC/OS provides common services such as package management, networking, logging and metrics, storage and volumes, and identity management.

Kubunetes
---------
Similar to DC/OS Marathon and Docker Swarm, Kubernetes is a platform for automating deployment, scaling, and operations of application containers across clusters of hosts, providing container-centric infrastructure. Kubernetes targets the management of elastic applications that consist of multiple microservices communicating with each other.

A pod is a group of one or more containers, the shared storage for those containers, and options about how to run the containers. Pods are always co-located and co-scheduled, and run in a shared context. A pod models an application-specific “logical host” (one or more application containers which are relatively tightly coupled). The shared context of a pod is a set of Linux namespaces, cgroups, and potentially other facets of isolation. Containers within a pod share an IP address and port space, and can find each other via localhost. They can also communicate with each other using standard IPC such as semaphores and shared memory. Containers in different pods have distinct IP addresses and can not communicate by IPC.

Pods are considered to be relatively ephemeral entities. They are created, destroyed and re-created on demand, based on the state of the server and the service itself. They won’t survive scheduling failures, node failures, or other evictions, such as due to lack of resources, or in the case of node maintenance. In general, users shouldn’t need to create pods directly. They should almost always use controllers, which provide self-healing with a cluster scope, as well as replication and rollout management.

As pods are mortal, their IP addresses cannot be relied upon to be stable over time. To solve this problem, Kubernetes has the concept of Service that defines a logical set of Pods and a policy by which to access them. The set of Pods targeted by a Service is (usually) determined by a Label Selector. For Kubernetes-native applications, Kubernetes offers a simple Endpoints API that is updated whenever the set of Pods in a Service changes.

![Kubernetes Architecture](https://s3-us-west-2.amazonaws.com/x-team-ghost-images/2016/06/o7leok.png)

### Master
The Master node is responsible for the management of Kubernetes cluster, i.e. orchestrating the worker nodes, where the actual services are running.

### API server
The API server is the entry points for all the REST commands used to control the cluster.

### etcd
etcd is a simple, distributed, consistent key-value store. It’s mainly used for shared configuration and service discovery. The data in etcd includes jobs being scheduled, created and deployed pod/service details and state, namespaces and replication informations, etc.

### scheduler
The Kubernetes scheduler is a policy-rich, topology-aware, workload-specific function that significantly impacts availability, performance, and capacity. The scheduler needs to take into account individual and collective resource requirements, quality of service requirements, hardware/software/policy constraints, affinity and anti-affinity specifications, data locality, inter-workload interference, deadlines, and so on.

### controller-manager
A controller uses API server to watch the shared state of the cluster and makes corrective changes to the current state to being it to the desired one. For example, the Replication controller takes care of the number of pods in the system. Additionally, we have endpoints controller, namespace controller, and serviceaccounts controller, etc.

### Worker
The pods are run on worker nodes.

### kubelet
kubelet gets the configuration of a pod from the API server and ensures that the described containers are up and running.

### kube-proxy
kube-proxy acts as a network proxy and a load balancer for a service on a single worker node. It takes care of the network routing for TCP and UDP packets.

