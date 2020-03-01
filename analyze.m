function void = analyze(directory)
	global sessionDate 	="10-12-2019 20:15";
	global echoDelay   	="E0669";
	global echoInstant 	="E0000";
	global image	   	="M6575";
	global audio		="A2680";
	global vehicle		="V9784";
	global path 		=[directory "/"];
	
	analyzeResponseTimes("delay", echoDelay, {"G1" "G5"});
	analyzeThroughput("delay", echoDelay, {"G2" "G6"});
	analyzeResponseTimes("instant", echoInstant, {"G3" "G7"});
	analyzeThroughput("instant", echoInstant, {"G4" "G8"});
	
	analyzeAudio("DPCM", [audio "T999"], "(random \"song\" from frequency generator)", {"G9" "G11" "G13"});
	analyzeAudio("AQDPCM", [audio "AQF998"], "(random song from Ithaki repertoire)", {"G10" "G12" "G14"});
	analyzeQuantizer("998", {"G15" "G16"});
	analyzeQuantizer("999", {"G17" "G18"});
	analyzeVehicle();

	close all;
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

function void = analyzeResponseTimes(mode, echoCode, gNames)
	global sessionDate;
	global path;
	
	data = importdata([path "responseTime" echoCode ".txt"]);
	gTitle = [sessionDate " " echoCode " Response time echo " mode " (9070)"];
				
	saveGraph(data, gTitle, "Packet", "Delay in ms", gNames{1}, "plot");
	saveGraph(data, gTitle, "Delay in ms", "Number of packet", gNames{2}, "hist");
	if strcmp(gNames{1}, "G1")
		delayMean = mean(data)
		delayDeviation = std(data)
	end
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

function void = analyzeThroughput(mode, echoCode, gNames)
	global sessionDate;
	global path;

	data = importdata([path "responseTime_throughput" echoCode ".txt"]);
	gTitle = [sessionDate " " echoCode " Throughput echo " mode " (9070)"];

	% Throughput will be computed as packets per second
	pps = [];
	cumulative = cumsum(data);
	frame = 8; % previous seconds for throughut calculation
	duration = 240; % 4 minutes 
	i = 1;
	j = 1;
	n = length(cumulative);
	for time=1:duration
		while j<n && cumulative(j+1)<=time*1000
			j = j+1;
		end
		while cumulative(j)-cumulative(i) >frame*1000
			i = i+1;
		end
		pps(time) = (j-i+1)/frame;
	end
	saveGraph(pps, gTitle, "Time elapsed in seconds", "Packets per second", gNames{1}, "plot");
	saveGraph(pps, gTitle, "Packets per second", "Frequency", gNames{2}, "hist");
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

function void = analyzeAudio(mode, echoCode, description, gNames)
	global path;
	global sessionDate;
	deltas = importdata([path "sound" mode echoCode ".txt"]);
	samples = cumsum(deltas);
	
	gTitle = [sessionDate " " echoCode " Audio sample analysis of\n" mode " " description " (9070)"];
	saveGraph(samples, gTitle, "# of sample", "Sample value", gNames{1}, "plot");
	saveGraph(deltas,  gTitle, "# of sample", "Delta value",  gNames{2}, "plot");
	saveGraph(samples, gTitle, "Sample value", "Number of samples", gNames{3}, "hist");
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

function void = analyzeQuantizer(version, gNames)
	global path;
	global audio;
	global sessionDate;
	data = importdata([path "soundAQDPCM" audio "AQF" version "quantizer.txt"]);
	gTitle = [sessionDate audio "AQF" version " quantizer analysis (9070)"];
	saveGraph(data(:,1), gTitle, "# of packet", "Mean value", gNames{1}, "plot");
	saveGraph(data(:,2), gTitle, "# of packet", "Step of quantizer", gNames{2}, "plot");
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

function void = analyzeVehicle()
	global vehicle;
	global path;
	[path "car" vehicle "OBD=01 .txt"]
	data = importdata([path "car" vehicle "OBD=01 .txt"]);
	for column=1:6
		data(:,column) = fixMissingValues(data(:,column));
	end
	
	subplot(2,3,1);
	saveGraph(data(:,1), "Engine run time", "Elapsed time in s", "seconds", "noPrint", "plot");
	subplot(2,3,2);
	saveGraph(data(:,2), "Intake air temperature", "Elapsed time in s", "Celsius degrees", "noPrint", "plot");
	subplot(2,3,3);
	saveGraph(data(:,3), "Throttle position", "Elapsed time in s", "Percent", "noPrint", "plot");
	subplot(2,3,4);
	saveGraph(data(:,4), "Rotations per minute", "Elapsed time in s", "rpm", "noPrint", "plot");
	subplot(2,3,5);
	saveGraph(data(:,5), "Vehicle speed", "Elapsed time in s", "km/h", "noPrint", "plot");
	subplot(2,3,6);
	saveGraph(data(:,6), "Coolant temperature", "Elapsed time in s", "Celsius degrees", "noPrint", "plot");
	print(gcf, [path "vehicle"], "-dsvg");
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

function fixed = fixMissingValues(data)
	for i=2:size(data)
		if data(i)==-100
			data(i) = data(i-1);
		end
	end
	for i=size(data)-1:-1:1
		if data(i)==-100
			data(i) = data(i+1);
		end
	end
	fixed = data;
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

function saveGraph(data, gTitle, xName, yName, gName, type)
	%~ gName
	global path;
	if strcmp(type,"plot")
		plot(data);
	else 
		hist(data,10);
	end
	grid on;
	title(gTitle);
	xlabel(xName);
	ylabel(yName);
	if !strcmp(gName, "noPrint")
		print([path gName],"-dsvg");
	end
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
