package org.thoughtworks.app.timesheetTracker.repository;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;
import org.thoughtworks.app.timesheetTracker.controller.TimeSheetTrackerController;
import org.thoughtworks.app.timesheetTracker.models.Employee;
import org.thoughtworks.app.timesheetTracker.models.MissingTimeSheetData;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.lang.String.valueOf;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;


@Repository

public class S3Client {

  @Autowired
  private Environment env;

  @Autowired
  private AmazonS3Client amazonS3Client;

  @Autowired
  private ObjectMapper mapper;

  private final static Logger logger = LoggerFactory.getLogger(S3Client.class);

  @Cacheable("timeSheetFileForLastWeek")
  public List<MissingTimeSheetData> getTimeSheetFileForLastWeek() {
    final String filePrefix = env.getProperty("cloud.aws.weekly.timesheet.file.prefix");
    return fetchFileFromAWS().andThen(parseMissingTimeSheetData()).apply(filePrefix);
  }

  @Cacheable("employeesNamesOfMissingTimeSheet")
  public List<MissingTimeSheetData> getTimeSheetFileForProjectLastWeek() {
    final String filePrefix =
        env.getProperty("cloud.aws.weekly.project.timeseet.mising.file.prefix");
    return fetchFileFromAWS().andThen(parseMissingTimeSheetData()).apply(filePrefix);
  }

  @Cacheable("totalEmployees")
  public List<Employee> getAllEmployees(){
    final String filePrefix = env.getProperty("cloud.aws.weekly.total.employees.file.prefix");
    return fetchFileFromAWS().andThen(parseEmployeeData()).apply(filePrefix);
  }

  private Function<String, S3ObjectInputStream> fetchFileFromAWS() {
    return filePrefix -> {
      logger.info("Fetching file from aws");

      final S3Object s3Object =
          amazonS3Client.getObject(env.getProperty("cloud.aws.timesheet.bucket.name"),
              String.format(filePrefix, getPreviousWeek()));
      try {
        return s3Object.getObjectContent();
      } catch (Exception e) {
        logger.info("Fetching file failed from aws");
        logger.info(e.getMessage());
      }
      return null;
    };
  }

  private Function<InputStream, List<Employee>> parseEmployeeData() {
      return input -> {
        try {
          return new JSONArray(IOUtils.toString(input)).toList().stream()
              .map(e-> {
                Map employee = mapper.convertValue(e, Map.class);
                return Employee.builder()
                    .employeeId(valueOf(employee.getOrDefault("id","")))
                    .employeeName(valueOf(employee.getOrDefault("name","")))
                    .role(valueOf(employee.getOrDefault("role","")).toUpperCase())
                    .country(valueOf(employee.getOrDefault("country","")).toUpperCase())
                    .workingLocation(valueOf(employee.getOrDefault("working-office","")).toUpperCase())
                    .build();
              })
              .collect(toList());
        } catch (IOException e) {
          logger.info(e.getMessage());
        }
        return emptyList();
      };
  }

  private Function<InputStream, List<MissingTimeSheetData>> parseMissingTimeSheetData() {
    return input -> {
      try {
        return new JSONArray(IOUtils.toString(input)).toList().stream()
            .map(e -> {
              Map timeSheetDataMap = mapper.convertValue(e, Map.class);

              return MissingTimeSheetData.builder()
                  .country(valueOf(timeSheetDataMap.getOrDefault("country", "")).toUpperCase())
                  .employeeId(valueOf(timeSheetDataMap.getOrDefault("id", "")))
                  .workingLocation(valueOf(timeSheetDataMap.getOrDefault("working-office", "")).toUpperCase())
                  .projectName(valueOf(timeSheetDataMap.getOrDefault("project-name", "")).toUpperCase())
                  .employeeName(valueOf(timeSheetDataMap.getOrDefault("name", "")))
                  .build();
            })
            .collect(toList());
      } catch (IOException e) {
        logger.info(e.getMessage());
      }
      return emptyList();
    };
  }

  private int getPreviousWeek() {
    return Calendar.getInstance().get(Calendar.WEEK_OF_YEAR) - 1;
  }

}

