/**
 * Copyright 2011 Intuit Inc. All Rights Reserved
 */
package com.intuit.tank.dao;

/*
 * #%L
 * Data Access
 * %%
 * Copyright (C) 2011 - 2015 Intuit Inc.
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import java.util.Date;
import java.util.List;

import javax.annotation.Nonnull;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.Projections;
import org.hibernate.transform.Transformers;

import com.intuit.tank.project.JobConfiguration;
import com.intuit.tank.project.Project;
import com.intuit.tank.project.ProjectDTO;
import com.intuit.tank.project.Workload;

/**
 * ProductDao
 * 
 * @author dangleton
 * 
 */
public class ProjectDao extends OwnableDao<Project> {

    /**
     * @param entityClass
     */
    public ProjectDao() {
        super();
        setReloadEntities(true);
    }

    /**
     * @param id
     * @return
     */
    @Nonnull
    public List<Workload> getWorkloadsForProject(@Nonnull Integer projectId) {
        return findById(projectId).getWorkloads();
    }

    /**
     * 
     * @param name
     * @return
     */
    public Project findByName(String name) {
        String prefix = "x";
        NamedParameter parameter = new NamedParameter(Project.PROPERTY_NAME, "n", name);
        StringBuilder sb = new StringBuilder();
        sb.append(buildQlSelect(prefix)).append(startWhere())
                .append(buildWhereClause(Operation.EQUALS, prefix, parameter));
        return super.findOneWithJQL(sb.toString(), parameter);
    }

    /**
     * @param project
     * @return
     */
    public synchronized Project saveOrUpdateProject(Project project) {
        boolean saveAs = project.getId() == 0;
        for (Workload w : project.getWorkloads()) {
            w.setParent(project);
            if (saveAs) {
                w.setId(0);
            }
            JobConfiguration jobConfiguration = w.getJobConfiguration();
            jobConfiguration.setParent(w);
            jobConfiguration.setJobRegions(JobRegionDao.cleanRegions(jobConfiguration.getJobRegions()));
            // for (TestPl sg : w.getTestPlans()) {
            // sg.setParent(w);
            // if (saveAs) {
            // sg.setId(0);
            // }
            // for (ScriptGroupStep sgs : sg.getScriptGroupSteps()) {
            // sgs.setParent(sg);
            // if (saveAs) {
            // sgs.setId(0);
            // }
            // }
            // }
        }
        project = saveOrUpdate(project);
        return project;
    }

    @Override
    public Project saveOrUpdate(Project entity) throws HibernateException {
        entity.setModified(new Date());
        return super.saveOrUpdate(entity);
    }
    
    /**
     * Finds all Objects of type T_ENTITY
     * 
     * @return the nonnull list of entities
     * @throws HibernateException
     *             if there is an error in persistence
     */
    @Nonnull
    @Override
    public List<Project> findAll() throws HibernateException {
        List<Project> results = null;
        EntityManager em = getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Project> query = cb.createQuery(Project.class);
        Root<Project> o = query.from(Project.class);
        o.fetch("workloads", JoinType.LEFT).fetch("jobConfiguration", JoinType.LEFT);
        query.select(o);
   
        results = em.createQuery(query).getResultList();
        return results;
    }
    /**
     * Finds all Objects of type T_ENTITY
     * 
     * @return the nonnull list of entities
     * @throws HibernateException
     *             if there is an error in persistence
     */
    @Nonnull
    public List<ProjectDTO> findAllProjectNames() throws HibernateException {
    	List<ProjectDTO> results = null;
    	EntityManager em = getEntityManager();
    	Session session = em.unwrap(Session.class);
    	Criteria cr = session.createCriteria(Project.class)
    			.setProjection(Projections.projectionList()
    					.add( Projections.property("id"), "id")
    					.add( Projections.property("created"), "created")
    					.add( Projections.property("modified"), "modified")
    					.add( Projections.property("creator"), "creator")
    					.add( Projections.property("name"), "name")
    					.add( Projections.property("scriptDriver"), "scriptDriver")
    					.add( Projections.property("productName"), "productName")
    					.add( Projections.property("comments"), "comments"))
    			.setResultTransformer(Transformers.aliasToBean(ProjectDTO.class));

        results = cr.list();
        return results;
    }

}
