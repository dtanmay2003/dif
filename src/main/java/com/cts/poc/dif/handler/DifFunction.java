package com.cts.poc.dif.handler;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.cts.poc.dif.entity.ConventionEntity;
import com.cts.poc.dif.entity.DistributorEntity;
import com.cts.poc.dif.entity.EmployeeEntity;
import com.cts.poc.dif.entity.ProcessSetupEntity;
import com.cts.poc.dif.model.Distributor;
import com.cts.poc.dif.service.DistributorService;
import com.google.gson.Gson;

@Component
public class DifFunction implements Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static Log logger = LogFactory.getLog(DifFunction.class);
	private Gson gson = new Gson();

	private final DistributorService distributorService;

	public DifFunction(final DistributorService distributorService) {
		this.distributorService = distributorService;
	}

	public APIGatewayProxyResponseEvent apply(APIGatewayProxyRequestEvent requestEvent) {
		logger.info("Request Payload : " + requestEvent.getBody());
		logger.info("Request Method : " + requestEvent.getHttpMethod());

		APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
		if ("GET".equals(requestEvent.getHttpMethod())) {
			logger.info("Step 1 Started");
			Distributor distributorModel = new Distributor();
			Map<String, String> requestParams = requestEvent.getQueryStringParameters();

			logger.info("Request Query Param : " + requestParams);
			DistributorEntity distributorEntity = getDistributor(Integer.valueOf(requestParams.get("distributor_id")));

			if (null == distributorEntity) {
				addCorsHeader(responseEvent);
				responseEvent.setBody(gson.toJson(distributorModel));
				responseEvent.setStatusCode(204);
			} else {
				BeanUtils.copyProperties(distributorEntity, distributorModel);
				addCorsHeader(responseEvent);
				responseEvent.setBody(gson.toJson(distributorModel));
				responseEvent.setStatusCode(200);
			}
			logger.info("Step 1 Finished");
		} else if ("POST".equals(requestEvent.getHttpMethod())) {
			logger.info("Step 2 Started");
			Distributor distributorModel = gson.fromJson(requestEvent.getBody(), Distributor.class);
			DistributorEntity distributorEntity = saveDistributor(distributorModel);

			BeanUtils.copyProperties(distributorEntity, distributorModel);
			addCorsHeader(responseEvent);
			responseEvent.setBody(gson.toJson(distributorModel));
			responseEvent.setStatusCode(201);
			logger.info("Step 2 Finished");
		} else if ("PATCH".equals(requestEvent.getHttpMethod())) {
			logger.info("Step 3 Started");
			Distributor distributorModel = new Distributor();
			DistributorEntity distributorEntity = distributorService.getDistributor();

			if (null == distributorEntity) {
				responseEvent.setBody(gson.toJson(distributorModel));
				responseEvent.setStatusCode(204);
			} else {
				BeanUtils.copyProperties(distributorEntity, distributorModel);
				responseEvent.setBody(gson.toJson(distributorModel));
				responseEvent.setStatusCode(200);
			}
			logger.info("Step 3 Finished");

			Map<String, String> header = requestEvent.getHeaders();
			if (null != header) {
				header.put("route", "prefinaloperation");
				responseEvent.setHeaders(header);
			} else {
				header = new HashMap<>();
				header.put("route", "prefinaloperation");
				responseEvent.setHeaders(header);
			}
		} else {
			Map<String, String> header = requestEvent.getHeaders();
			logger.info("RequestEvent header : " + header);

			if (null != header && !"finaloperation".equals(header.get("route"))) {
				logger.info("Step 4 Started");
				Distributor distributorModel = gson.fromJson(requestEvent.getBody(), Distributor.class);
				DistributorEntity distributorEntity = getDistributor(distributorModel.getDistributor_id());

				BeanUtils.copyProperties(distributorEntity, distributorModel);
				header.put("route", "finaloperation");
				responseEvent.setHeaders(header);
				responseEvent.setBody(gson.toJson(distributorModel));
				responseEvent.setStatusCode(distributorService.doPostRequestToDrm(distributorModel));
				logger.info("Step 4 Finished");
			} else if (null != header && "finaloperation".equals(header.get("route"))) {
				logger.info("Step 5 Started");
				Distributor distributorModel = gson.fromJson(requestEvent.getBody(), Distributor.class);
				distributorModel.getConvention().stream().filter(Objects::nonNull).forEach(convention -> {
					convention.getProcess().stream().filter(Objects::nonNull).forEach(process -> {
						process.setStatus("Verification In-progress");
					});
				});
				logger.info("Step 5 before saving the DistributorModel is : " + distributorModel);
				DistributorEntity distributorEntity = saveDistributor(distributorModel);

				BeanUtils.copyProperties(distributorEntity, distributorModel);
				responseEvent.setBody(gson.toJson(distributorModel));
				responseEvent.setStatusCode(200);
				logger.info("Step 5 Finished");
			}
		}

		logger.info("Response Header : " + responseEvent.getHeaders());
		logger.info("Response Payload : " + responseEvent.getBody());
		return responseEvent;
	}

	private DistributorEntity getDistributor(Integer id) {
		return distributorService.getDistributor(id);
	}

	public DistributorEntity saveDistributor(com.cts.poc.dif.model.Distributor distributorModel) {
		DistributorEntity distributorEntity = populateEntity(distributorModel);
		return distributorService.saveDistributor(distributorEntity);
	}

	private DistributorEntity populateEntity(com.cts.poc.dif.model.Distributor distributorModel) {
		DistributorEntity distributorEntity = new DistributorEntity();
		distributorEntity.setDistributor_id(distributorModel.getDistributor_id());
		distributorEntity.setFsma_status(distributorModel.getFsma_status());
		distributorEntity.setAxa_distributor_status(distributorModel.getAxa_distributor_status());
		distributorEntity.setOrganization_type(distributorModel.getOrganization_type());
		distributorEntity.setContract_type(distributorModel.getContract_type());
		distributorEntity.setProcess_manager_identifier(distributorModel.getProcess_manager_identifier());
		distributorEntity.setName(distributorModel.getName());
		distributorEntity.setEstablishment_date(new Date());
		distributorEntity.setEnterprise_number(distributorModel.getEnterprise_number());
		distributorEntity.setLineofbusiness(distributorModel.getLineofbusiness());
		// distributorEntity.setLvf(null != distributorModel.getLvf() ?
		// distributorModel.getLvf().charAt(0) : null);

		Set<com.cts.poc.dif.entity.ConventionEntity> conventionEntitySet = new HashSet<>();
		distributorModel.getConvention().forEach(convention -> {
			ConventionEntity conventionEntity = new ConventionEntity();
			conventionEntity.setConvention_id(convention.getConvention_id());
			conventionEntity.setProcess_manager_identifier(convention.getProcess_manager_identifier());
			conventionEntity.setSegment_type(convention.getSegment_type());
			conventionEntity.setCreated_by(convention.getCreated_by());
			conventionEntity.setCreated_date(new Date());
			conventionEntity.setModified_by(convention.getModified_by());
			conventionEntity.setModified_date(new Date());
			conventionEntity.setValid_from(new Date());
			conventionEntity.setValid_to(new Date());

			Set<com.cts.poc.dif.entity.ProcessSetupEntity> processSetupEntitySet = new HashSet<>();
			convention.getProcess().forEach(process -> {
				ProcessSetupEntity processSetupEntity = new ProcessSetupEntity();
				processSetupEntity.setProcess_id(process.getProcess_id());
				processSetupEntity.setSubprocess(process.getSubprocess());
				processSetupEntity.setProcess_manager_identifier(process.getProcess_manager_identifier());
				processSetupEntity.setStatus(process.getStatus());
				processSetupEntity.setCreate_date(new Date());
				processSetupEntitySet.add(processSetupEntity);
			});
			conventionEntity.setProcess(processSetupEntitySet);
			conventionEntitySet.add(conventionEntity);
		});
		distributorEntity.setConvention(conventionEntitySet);

		Set<EmployeeEntity> employeeEntitySet = new HashSet<>();
		distributorModel.getEmployee().forEach(employee -> {
			EmployeeEntity employeeEntity = new EmployeeEntity();
			employeeEntity.setEmployee_id(employee.getEmployee_id());
			employeeEntity.setProcess_manager_identifier(employee.getProcess_manager_identifier());
			employeeEntity.setFirst_name(employee.getFirst_name());
			employeeEntity.setLast_name(employee.getLast_name());
			employeeEntity.setLvf(null != employee.getLvf() ? employee.getLvf().charAt(0) : null);
			employeeEntity.setGender(null != employee.getGender() ? employee.getGender().charAt(0) : null);
			employeeEntity.setNationality(employee.getNationality());
			employeeEntity.setActivity_type(employee.getActivity_type());
			employeeEntity.setSpecialization_type(employee.getSpecialization_type());
			employeeEntity.setNi_number(employee.getNi_number());
			employeeEntity.setBehavior_type(employee.getBehavior_type());
			employeeEntity.setCertification(employee.getCertification());
			employeeEntity
					.setManager_id(null != employee.getManager_id() ? Integer.valueOf(employee.getManager_id()) : null);
			employeeEntity.setHierarchy_type(employee.getHierarchy_type());
			employeeEntitySet.add(employeeEntity);
		});
		distributorEntity.setEmployee(employeeEntitySet);

		return distributorEntity;
	}

	private void addCorsHeader(APIGatewayProxyResponseEvent responseEvent) {
		Map<String, String> header = responseEvent.getHeaders();
		if (null != header) {
			setCorsHeader(header);
			responseEvent.setHeaders(header);
		} else {
			header = new HashMap<>();
			setCorsHeader(header);
			responseEvent.setHeaders(header);
		}
	}

	private void setCorsHeader(Map<String, String> header) {
		header.put("Access-Control-Allow-Headers", "Content-Type");
		header.put("Access-Control-Allow-Origin", "*");
		header.put("Access-Control-Allow-Methods", "OPTIONS,POST,GET");
	}
}
