/**
 * This file is part of Eclipse Steady.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2018 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.sap.psr.vulas.backend.requests;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sap.psr.vulas.backend.BackendConnectionException;
import com.sap.psr.vulas.backend.HttpMethod;
import com.sap.psr.vulas.backend.HttpResponse;
import com.sap.psr.vulas.core.util.CoreConfiguration;
import com.sap.psr.vulas.goals.GoalContext;

/**
 * <p>ConditionalHttpRequest class.</p>
 *
 */
public class ConditionalHttpRequest extends BasicHttpRequest {

	private static final Log log = LogFactory.getLog(ConditionalHttpRequest.class);

	private List<ResponseCondition> conditions = new LinkedList<ResponseCondition>();

	private BasicHttpRequest conditionRequest = null;

	/**
	 * <p>Constructor for ConditionalHttpRequest.</p>
	 *
	 * @param _method a {@link com.sap.psr.vulas.backend.HttpMethod} object.
	 * @param _path a {@link java.lang.String} object.
	 * @param _query_string_params a {@link java.util.Map} object.
	 */
	public ConditionalHttpRequest(HttpMethod _method, String _path, Map<String,String> _query_string_params) {
		super(_method, _path, _query_string_params);
	}

	/**
	 * <p>Setter for the field <code>conditionRequest</code>.</p>
	 *
	 * @param _cr a {@link com.sap.psr.vulas.backend.requests.BasicHttpRequest} object.
	 * @return a {@link com.sap.psr.vulas.backend.requests.ConditionalHttpRequest} object.
	 */
	public ConditionalHttpRequest setConditionRequest(BasicHttpRequest _cr) { this.conditionRequest = _cr; return this; }

	/**
	 * Adds a condition to the list of conditions that must be met before the actual request is sent.
	 *
	 * @param _condition a {@link com.sap.psr.vulas.backend.requests.ResponseCondition} object.
	 * @return a {@link com.sap.psr.vulas.backend.requests.ConditionalHttpRequest} object.
	 */
	public ConditionalHttpRequest addCondition(ResponseCondition _condition) {
		this.conditions.add(_condition);
		return this;
	}
	
	/** {@inheritDoc} */
	@Override
	public HttpRequest setGoalContext(GoalContext _ctx) {
		this.context = _ctx;
		if(this.conditionRequest!=null)
			this.conditionRequest.setGoalContext(_ctx);
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * First performs the conditional requests. Only if all the responses meets the condition, the actual request will be performed.
	 */
	@Override
	public HttpResponse send() throws IllegalStateException, BackendConnectionException {
		if(this.conditionRequest==null || this.conditions.size()==0)
			throw new IllegalStateException("No condition request or no conditions set");

		// Conditional requests will be skipped in offline mode
		if(CoreConfiguration.isBackendOffline(this.getVulasConfiguration())) {
			ConditionalHttpRequest.log.info("Condition(s) not evaluated due to offline mode, do " + this.toString());
			return super.send();
		}
		// Perform conditional requests
		else {
			// Indicates whether all conditions are met
			boolean meets = true;
			final HttpResponse condition_response = this.conditionRequest.send();
			for(ResponseCondition rc: this.conditions) {
				meets = meets && rc.meetsCondition(condition_response);
				if(!meets) {
					ConditionalHttpRequest.log.info("Condition " + rc + " not met");
					break;
				} else {
					ConditionalHttpRequest.log.info("Condition " + rc + " met");
				}
			}

			// Only send if they are met
			if(meets) {
				ConditionalHttpRequest.log.info("Condition(s) met, do " + this.toString());
				return super.send();
			}
			else {
				ConditionalHttpRequest.log.info("Condition(s) not met, skip " + this.toString());
				return null;
			}	
		}	
	}
	
	/** {@inheritDoc} */
	@Override
	public void savePayloadToDisk() throws IOException {
		super.savePayloadToDisk();
		if(this.conditionRequest!=null)
			this.conditionRequest.savePayloadToDisk();
	}

	/** {@inheritDoc} */
	@Override
	public void loadPayloadFromDisk() throws IOException {
		super.loadPayloadFromDisk();
		if(this.conditionRequest!=null)
			this.conditionRequest.loadPayloadFromDisk();
	}
	
	/** {@inheritDoc} */
	@Override
	public void deletePayloadFromDisk() throws IOException {
		super.deletePayloadFromDisk();
		if(this.conditionRequest!=null)
			this.conditionRequest.deletePayloadFromDisk();
	}
	
	/**
	 * First calls the default method {@link ObjectInputStream#defaultReadObject()}, then calls {@link HttpRequest#loadFromDisk()}
	 * @param in
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		super.loadPayloadFromDisk();
		this.loadPayloadFromDisk();
	}
}