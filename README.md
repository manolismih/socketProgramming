# Java network socket programming
_Programming assignment for the Computer Networks ΙΙ course, school of Electrical and Computer Enginneering, AUTH._  
_December of 2019_

## Project description
Implementation of a Java application that connects with a remote server and communicates with it via UDP packets. The required [userApplication](userApplication.java) functions are:
* Estimating the response time (ping) of the server.
* Estimating the throughput of the server.
* Receiving a bitmap image.
* Receiving, decoding and playing digital PCM modulated audio clips.
* Receiving (via the server) temperature measurements from remote stations and altitude measurements from a drone.
* Receiveing Onboard Diagnostics II (SAE J1979 standard) from the server.

In addition, a [matlab script](analyze.m) is created for analyzing and plotting the measurements obtained from the java application into diagrams G1..G20 for 2 diferent time sessions. 
