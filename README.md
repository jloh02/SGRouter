# SGRouter
SGRouter is a project focused on public transit routing in Singapore

## Deployment
Designed to be deployed on [Google App Engine (GAE)](https://cloud.google.com/appengine)

## Components
SGRouter consists of 2 different services
- [graph_builder_service](./graph_builder_service): Programmatically creates a graph detailing Singapore's public transit system network
- [routing_service](./routing_service): Finds shortest path using graph generated by graph_builder_service
