package org.svnadmin.servlet;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.svnadmin.entity.PjGr;
import org.svnadmin.service.PjGrService;
import org.svnadmin.util.SpringUtils;

public class PjGrServlet extends PjBaseServlet {
	private static final long serialVersionUID = 1L;

	PjGrService pjGrService = SpringUtils.getBean(PjGrService.BEAN_NAME);

	@Override
	protected void before(HttpServletRequest request,
			HttpServletResponse response) {
		if (StringUtils.isBlank(request.getParameter("pj"))) {
			throw new RuntimeException("不可以直接访问，请从项目的菜单进来这个页面!");
		}
	}

	@Override
	protected void get(HttpServletRequest request, HttpServletResponse response) {
		request.setAttribute(
				"entity",
				pjGrService.get(request.getParameter("pj"),
						request.getParameter("gr")));
	}

	@Override
	protected void delete(HttpServletRequest request,
			HttpServletResponse response) {
		pjGrService.delete(request.getParameter("pj"),
				request.getParameter("gr"));
	}

	@Override
	protected void save(HttpServletRequest request, HttpServletResponse response) {

		PjGr entity = new PjGr();
		entity.setPj(request.getParameter("pj"));
		entity.setGr(request.getParameter("gr"));
		entity.setDes(request.getParameter("des"));

		pjGrService.save(entity);

		request.setAttribute("entity", entity);
	}

	@Override
	protected void list(HttpServletRequest request, HttpServletResponse response) {
		List<PjGr> list = pjGrService.list(request.getParameter("pj"));
		request.setAttribute("list", list);
	}

	@Override
	protected void forword(HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		request.getRequestDispatcher("pjgr.jsp").forward(request, response);
	}

}