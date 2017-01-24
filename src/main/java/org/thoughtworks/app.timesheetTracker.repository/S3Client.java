package org.thoughtworks.app.timesheetTracker.repository;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;
import org.thoughtworks.app.timesheetTracker.models.MissingTimeSheetData;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.valueOf;


@Repository
public class S3Client {

    @Autowired
    private Environment env;

    public List<MissingTimeSheetData> getTimeSheetFileForLastWeek() {
        final AmazonS3Client amazonS3Client = new AmazonS3Client(new DefaultAWSCredentialsProviderChain());

        final S3Object s3Object = amazonS3Client.getObject(env.getProperty("cloud.aws.timesheet.bucket.name"),
                                String.format(env.getProperty("cloud.aws.weekly.timesheet.file.prefix"), getPreviousWeek()));
        try {
            return parseEmployeeData(s3Object.getObjectContent());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private List<MissingTimeSheetData> parseEmployeeData(InputStream input) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        return new JSONArray(IOUtils.toString(input)).toList().stream()
                .map(e -> {
                    Map timeSheetDataMap = mapper.convertValue(e, Map.class);

                    return MissingTimeSheetData.builder()
                            .country(valueOf(timeSheetDataMap.get("country")).toUpperCase())
                            .employeeId(valueOf(timeSheetDataMap.get("id")).toUpperCase())
                            .workingLocation(valueOf(timeSheetDataMap.get("working-office")).toUpperCase())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private int getPreviousWeek() {
        return Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)-1;
    }
}

